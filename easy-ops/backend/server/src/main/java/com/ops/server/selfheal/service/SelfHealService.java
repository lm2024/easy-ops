package com.ops.server.selfheal.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.NotificationRecordModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.SelfHealEventModel;
import com.ops.common.model.SelfHealPolicyModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.mapper.SelfHealEventMapper;
import com.ops.server.mapper.SelfHealPolicyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自愈编排服务：进程检测、自动重启、熔断与通知
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class SelfHealService {

    private static final Logger log = LoggerFactory.getLogger(SelfHealService.class);

    static final String EVT_DETECT_DOWN = "DETECT_DOWN";
    static final String EVT_RETRY = "RETRY";
    static final String EVT_RECOVER = "RECOVER";
    static final String EVT_FAIL = "FAIL";
    static final String EVT_CIRCUIT_BREAK = "CIRCUIT_BREAK";
    static final String EVT_OFFLINE = "OFFLINE";

    @Autowired
    private SelfHealPolicyMapper policyMapper;

    @Autowired
    private SelfHealEventMapper eventMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailNotifyService emailNotifyService;

    /** 节点级锁，避免同一节点并发重启 */
    private final ConcurrentHashMap<String, Boolean> nodeLocks = new ConcurrentHashMap<>();

    /**
     * 对单条策略执行检测与自愈
     */
    public void checkPolicy(SelfHealPolicyModel policy) {
        if (policy == null || policy.getEnabled() == null || policy.getEnabled() != 1) {
            return;
        }
        if (policy.getCircuitBreaker() != null && policy.getCircuitBreaker() == 1) {
            return;
        }

        ProjectModel project = projectMapper.findById(policy.getProjectId());
        if (project == null || project.getNodeIds() == null || project.getNodeIds().isEmpty()) {
            return;
        }

        String[] nodeIdArr = project.getNodeIds().split(",");
        for (String nodeIdStr : nodeIdArr) {
            if (nodeIdStr == null || nodeIdStr.trim().isEmpty()) {
                continue;
            }
            try {
                Long nodeId = Long.parseLong(nodeIdStr.trim());
                checkNode(policy, project, nodeId);
            } catch (NumberFormatException e) {
                log.warn("Invalid nodeId in project {}: {}", policy.getProjectId(), nodeIdStr);
            }
        }
    }

    private void checkNode(SelfHealPolicyModel policy, ProjectModel project, Long nodeId) {
        String lockKey = project.getId() + ":" + nodeId;
        if (nodeLocks.putIfAbsent(lockKey, Boolean.TRUE) != null) {
            return;
        }
        try {
            doCheckNode(policy, project, nodeId);
        } finally {
            nodeLocks.remove(lockKey);
        }
    }

    private void doCheckNode(SelfHealPolicyModel policy, ProjectModel project, Long nodeId) {
        NodeModel node = nodeMapper.findById(nodeId);
        if (node == null) {
            return;
        }
        if (node.getStatus() != null && node.getStatus() != 1) {
            recordEvent(policy, project.getId(), nodeId, EVT_OFFLINE, 0, "节点离线，跳过自愈");
            return;
        }

        String deployDir = project.getDeployDir() != null ? project.getDeployDir() : "/app/data";
        String jarName = project.getJarName() != null ? project.getJarName() : "app.jar";

        Map<String, Object> status = agentClient.getProcessStatus(node, deployDir, jarName);
        boolean alive = isProcessAlive(status);
        SelfHealEventModel latest = eventMapper.findLatest(project.getId(), nodeId);

        if (alive) {
            if (latest != null && (EVT_DETECT_DOWN.equals(latest.getEventType())
                    || EVT_RETRY.equals(latest.getEventType()))) {
                recordEvent(policy, project.getId(), nodeId, EVT_RECOVER, 0, "进程已恢复运行");
            }
            return;
        }

        int currentRetry = resolveRetryCount(latest);
        if (latest == null || EVT_RECOVER.equals(latest.getEventType())
                || EVT_FAIL.equals(latest.getEventType()) || EVT_CIRCUIT_BREAK.equals(latest.getEventType())) {
            recordEvent(policy, project.getId(), nodeId, EVT_DETECT_DOWN, 0, "检测到进程 DOWN");
            currentRetry = 0;
        }

        int maxRetries = policy.getMaxRetries() != null ? policy.getMaxRetries() : 3;
        if (currentRetry < maxRetries) {
            int nextRetry = currentRetry + 1;
            Map<String, String> body = buildRestartBody(project, deployDir);
            agentClient.restartProcess(node, project.getId(), body);
            recordEvent(policy, project.getId(), nodeId, EVT_RETRY, nextRetry,
                    "第" + nextRetry + "次自动重启");
            return;
        }

        handleFailure(policy, project, node, maxRetries);
    }

    private void handleFailure(SelfHealPolicyModel policy, ProjectModel project, NodeModel node, int maxRetries) {
        recordEvent(policy, project.getId(), node.getId(), EVT_FAIL, maxRetries,
                maxRetries + "次重启均失败，进程无法保持运行");

        long now = System.currentTimeMillis();
        policyMapper.updateCircuitBreaker(project.getId(), 1, now, now);
        recordEvent(policy, project.getId(), node.getId(), EVT_CIRCUIT_BREAK, maxRetries, "已触发熔断");

        String title = "【" + project.getName() + "】" + node.getName() + " 自愈失败";
        String content = "项目 " + project.getName() + " 在节点 " + node.getName()
                + " 上连续" + maxRetries + "次自动重启失败，已触发熔断。请立即介入处理。";

        if (policy.getNotifyEmail() != null && policy.getNotifyEmail() == 1) {
            emailNotifyService.sendSelfHealFailEmail(title, content, project.getId(), node.getId());
        }

        if (policy.getNotifyPopup() != null && policy.getNotifyPopup() == 1) {
            NotificationRecordModel notification = new NotificationRecordModel();
            notification.setType("ALERT");
            notification.setLevel("CRITICAL");
            notification.setTitle(title);
            notification.setContent(content);
            notification.setProjectId(project.getId());
            notification.setNodeId(node.getId());
            notification.setSourceType("SELF_HEAL");
            notification.setRequireAck(1);
            notification.setBroadcast(1);
            notificationService.create(notification);
        }
    }

    private Map<String, String> buildRestartBody(ProjectModel project, String deployDir) {
        Map<String, String> body = new HashMap<>();
        body.put("deployDir", deployDir);
        body.put("jarName", project.getJarName());
        body.put("startScript", project.getStartScript() != null ? project.getStartScript() : "sh start.sh");
        if (project.getStopScript() != null) {
            body.put("stopScript", project.getStopScript());
        }
        return body;
    }

    private boolean isProcessAlive(Map<String, Object> status) {
        if (status == null) {
            return false;
        }
        Object alive = status.get("alive");
        if (alive instanceof Boolean) {
            return (Boolean) alive;
        }
        Object running = status.get("running");
        return running instanceof Boolean && (Boolean) running;
    }

    private int resolveRetryCount(SelfHealEventModel latest) {
        if (latest == null) {
            return 0;
        }
        if (EVT_RETRY.equals(latest.getEventType()) && latest.getRetryCount() != null) {
            return latest.getRetryCount();
        }
        if (EVT_DETECT_DOWN.equals(latest.getEventType())) {
            return 0;
        }
        return 0;
    }

    private void recordEvent(SelfHealPolicyModel policy, Long projectId, Long nodeId,
                             String eventType, int retryCount, String detail) {
        SelfHealEventModel event = new SelfHealEventModel();
        event.setProjectId(projectId);
        event.setNodeId(nodeId);
        event.setEventType(eventType);
        event.setRetryCount(retryCount);
        event.setMaxRetries(policy.getMaxRetries());
        event.setDetail(detail);
        event.setCreateTime(System.currentTimeMillis());
        eventMapper.insert(event);
        log.info("Self-heal event: project={} node={} type={} detail={}", projectId, nodeId, eventType, detail);
    }
}
