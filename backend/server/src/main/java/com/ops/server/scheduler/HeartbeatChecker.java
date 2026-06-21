package com.ops.server.scheduler;

import com.ops.common.model.AlarmModel;
import com.ops.common.model.NodeModel;
import com.ops.server.mapper.AlarmRecordMapper;
import com.ops.server.mapper.NodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SEC-001: 心跳检测器 - 增加分布式锁
 * 原问题: @Scheduled 无分布式锁，多实例部署会导致重复告警/重复部署
 * 修复: 通过 DistributedLock 确保只有一个实例执行定时任务
 */
@Component
public class HeartbeatChecker {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatChecker.class);

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AlarmRecordMapper alarmRecordMapper;

    @Autowired
    private DistributedLock distributedLock;

    @Value("${server.heart-second:30}")
    private int heartSecond;

    @Value("${server.offline-second:90}")
    private int offlineSecond;

    private static final String LOCK_NAME_HEARTBEAT = "heartbeat_checker";

    @Scheduled(fixedRate = 10000)
    public void checkOffline() {
        // SEC-001: 分布式锁 - 仅单实例执行
        if (!distributedLock.tryLock(LOCK_NAME_HEARTBEAT)) {
            log.debug("HeartbeatChecker: lock not acquired by this instance, skipping");
            return;
        }

        try {
            doCheckOffline();
        } finally {
            distributedLock.releaseLock(LOCK_NAME_HEARTBEAT);
        }
    }

    private void doCheckOffline() {
        long cutoff = System.currentTimeMillis() - offlineSecond * 1000L;
        List<NodeModel> nodes = nodeMapper.getOfflineCandidates(cutoff);
        if (nodes == null || nodes.isEmpty()) return;

        for (NodeModel node : nodes) {
            Long nodeId = node.getId();
            String nodeName = node.getName();
            String nodeIp = node.getIp();

            if (nodeId == null) {
                log.warn("Offline candidate has null id, skipping");
                continue;
            }

            nodeMapper.updateStatusOffline(nodeId);
            log.info("Node {} ({}) marked as OFFLINE", nodeName, nodeIp);

            // Trigger alarm
            String content = "节点[" + (nodeName != null ? nodeName : "?" ) + "] " + (nodeIp != null ? nodeIp : "?") + " 心跳超时，状态变更为离线";
            AlarmModel alarm = new AlarmModel();
            alarm.setNodeId(nodeId);
            alarm.setType("OFFLINE");
            alarm.setContent(content);
            alarm.setSendResult(0); // PENDING
            alarm.setCreateTime(System.currentTimeMillis());
            alarmRecordMapper.insert(alarm);
        }
    }
}
