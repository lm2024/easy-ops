package com.ops.server.arthas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.model.ArthasDiagnoseRecordModel;
import com.ops.common.model.ArthasDiagnoseResultModel;
import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ArthasDiagnoseRecordMapper;
import com.ops.server.mapper.ArthasDiagnoseResultMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.util.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arthas 诊断业务服务（核心）
 * 负责诊断会话生命周期管理、命令透传与结果持久化
 */
@Service
public class ArthasDiagnoseService {
    private static final Logger log = LoggerFactory.getLogger(ArthasDiagnoseService.class);

    @Autowired
    private ArthasAgentProxy agentProxy;

    @Autowired
    private ArthasDiagnoseRecordMapper recordMapper;

    @Autowired
    private ArthasDiagnoseResultMapper resultMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private SecurityContext securityContext;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 单条命令结果入库的大小上限（KB），可通过 easyops.arthas.result-max-size-kb 调整。
     *
     * <p>profiler 火焰图 HTML、大型 trace 结果动辄数 MB，若整份塞进 CLOB，
     * 几次诊断就能把 H2 撑大到需要紧急救援（本项目已有过该故障）。
     * 超限时只存结构与预览，完整产物通过文件下载获取。
     */
    @Value("${easyops.arthas.result-max-size-kb:64}")
    private int resultMaxSizeKb;

    /** 截断结果中保留的预览字符数 */
    private static final int TRUNCATED_PREVIEW_CHARS = 2000;

    /** 结果入库上限（字节），配置值非法时回退到 64KB */
    private int maxResultBytes() {
        return (resultMaxSizeKb > 0 ? resultMaxSizeKb : 64) * 1024;
    }

    /**
     * 会话无活动多久判定为僵尸（分钟）。
     * 需与 Agent 侧的 agent.arthas.session-timeout-minutes 保持一致，
     * 否则会出现"Agent 已清掉物理会话、Server 仍认为有人在用"的错位。
     */
    @Value("${easyops.arthas.session-idle-timeout-minutes:60}")
    private int sessionIdleTimeoutMinutes;

    private int sessionIdleTimeoutMinutes() {
        return sessionIdleTimeoutMinutes > 0 ? sessionIdleTimeoutMinutes : 60;
    }

    /**
     * 启动诊断会话
     */
    public Map<String, Object> startDiagnose(Long projectId, Long nodeId, long pid) {
        // 权限校验
        if (!securityContext.hasProjectPermission(projectId)) {
            throw new RuntimeException("无权限访问该项目");
        }

        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }

        // 创建会话记录
        String sessionId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long now = System.currentTimeMillis();

        ArthasDiagnoseRecordModel record = new ArthasDiagnoseRecordModel();
        record.setSessionId(sessionId);
        record.setProjectId(projectId);
        record.setNodeId(nodeId);
        record.setPid((int) pid);
        record.setJarName(project.getJarName());
        record.setStatus("ATTACHING");
        record.setTriggerBy(securityContext.getCurrentUsername());
        record.setStartTime(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setTenantId(securityContext.getCurrentTenantId());
        recordMapper.insert(record);

        // 调用 Agent attach
        try {
            Map<String, Object> attachResult = agentProxy.attach(nodeId, pid, projectId);
            String arthasVersion = attachResult.get("arthasVersion") != null
                    ? attachResult.get("arthasVersion").toString() : "unknown";

            // 更新记录
            record.setStatus("RUNNING");
            record.setArthasVersion(arthasVersion);
            record.setUpdatedAt(System.currentTimeMillis());
            recordMapper.update(record);

            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", sessionId);
            data.put("recordId", record.getId());
            data.put("pid", pid);
            data.put("projectId", projectId);
            data.put("nodeId", nodeId);
            data.put("arthasVersion", arthasVersion);
            data.put("attachTime", now);
            data.put("status", "ATTACHED");
            log.info("Arthas 诊断会话启动成功: sessionId={}, projectId={}, nodeId={}, pid={}",
                    sessionId, projectId, nodeId, pid);
            return data;
        } catch (Exception e) {
            // attach 失败，更新记录
            record.setStatus("FAILED");
            record.setException(e.getMessage());
            record.setEndTime(System.currentTimeMillis());
            record.setUpdatedAt(System.currentTimeMillis());
            recordMapper.update(record);
            log.error("Arthas 诊断会话启动失败: sessionId={}, error={}", sessionId, e.getMessage());
            throw new RuntimeException("attach 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 结束诊断会话。
     *
     * <p><b>为什么不能无条件 detach：</b>Agent 侧的 Arthas 进程是按 <b>pid</b> 持有的
     * （一个目标进程只存在一个物理会话），而这里的诊断记录是按 <b>sessionId</b> 持有的（逻辑会话）。
     * 同一个应用的多个诊断页、多个用户会共享同一个物理会话，
     * 只要有一个人点"结束诊断"就 detach，其余使用者的会话会全部变成僵尸，
     * 下一条命令直接抛"会话不存在或已结束"。所以停止前必须确认没有别的使用者。
     */
    public Map<String, Object> stopDiagnose(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }

        long now = System.currentTimeMillis();

        // 先回收僵尸记录：浏览器直接关掉、进程被杀时没人会调 stop，
        // 这些残留会被下面的引用计数算成"还有人在用"，导致永远 detach 不掉。
        reapStaleRecords(now);

        int activePeers = record.getPid() == null ? 0
                : recordMapper.countActivePeers(record.getNodeId(), record.getPid(), sessionId);

        boolean detached = false;
        if (activePeers == 0) {
            try {
                agentProxy.detach(record.getNodeId(), record.getPid());
                detached = true;
            } catch (Exception e) {
                log.warn("detach 失败（可能会话已结束）: sessionId={}, error={}", sessionId, e.getMessage());
            }
        } else {
            log.info("目标进程上仍有 {} 个活跃会话，保留 Arthas attach: nodeId={}, pid={}",
                    activePeers, record.getNodeId(), record.getPid());
        }

        // 更新记录
        record.setStatus("FINISHED");
        record.setEndTime(now);
        record.setDurationMs((int) (now - record.getStartTime()));
        record.setUpdatedAt(now);
        recordMapper.update(record);

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("status", detached ? "DETACHED" : "CLOSED");
        data.put("detached", detached);
        data.put("activePeers", activePeers);
        data.put("durationMs", record.getDurationMs());
        log.info("Arthas 诊断会话结束: sessionId={}, durationMs={}, detached={}", sessionId, record.getDurationMs(), detached);
        return data;
    }

    /**
     * 回收长时间无活动的会话记录。
     * 阈值与 Agent 侧会话超时保持一致：Agent 都已经把物理会话清掉了，
     * Server 侧没理由继续认为它活着，否则引用计数会永远大于 0。
     */
    private void reapStaleRecords(long now) {
        try {
            long cutoff = now - Math.max(1, sessionIdleTimeoutMinutes()) * 60L * 1000L;
            List<String> statuses = java.util.Arrays.asList("ATTACHING", "RUNNING");
            int n = recordMapper.finishStale(cutoff, statuses);
            if (n > 0) {
                log.info("回收 {} 条无活动的 Arthas 诊断记录（超过 {} 分钟未活动）", n, sessionIdleTimeoutMinutes());
            }
        } catch (Exception e) {
            log.debug("回收无活动诊断记录失败（不影响本次操作）: {}", e.getMessage());
        }
    }

    /**
     * 执行命令并持久化结果
     */
    public Map<String, Object> execCommand(String sessionId, String command, int timeoutMs) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        if (!"RUNNING".equals(record.getStatus())) {
            throw new RuntimeException("诊断会话已结束: status=" + record.getStatus());
        }

        long execStart = System.currentTimeMillis();
        Map<String, Object> result;
        boolean reattached = false;
        try {
            result = agentProxy.exec(record.getNodeId(), record.getPid().longValue(), command, timeoutMs);
        } catch (Exception e) {
            // Agent 侧的 Arthas 进程会因为超时清理、Agent 重启、被同进程的其他会话 stop 而消失。
            // 站在用户角度这只是"用着用着突然报错"，完全无法理解；
            // 这类中断是可自愈的，先悄悄重新 attach 再重试一次，成功了就当没发生过。
            if (!isSessionLost(e)) {
                throw e;
            }
            log.warn("Arthas 会话已丢失，尝试自动重连: sessionId={}, nodeId={}, pid={}, error={}",
                    sessionId, record.getNodeId(), record.getPid(), rootMessage(e));
            reattach(record);
            reattached = true;
            execStart = System.currentTimeMillis();
            try {
                result = agentProxy.exec(record.getNodeId(), record.getPid().longValue(), command, timeoutMs);
            } catch (Exception retryEx) {
                // 重连后仍然失败，多半是目标进程真的没了，这时候才值得打断用户
                throw new RuntimeException(
                        "诊断连接已断开，自动重连后仍无法执行。请确认目标进程仍在运行，或重新进入诊断页。", retryEx);
            }
        }
        int durationMs = (int) (System.currentTimeMillis() - execStart);

        // 持久化结果（超大结果截断，避免撑爆数据库）
        boolean success = result.get("success") != null && Boolean.TRUE.equals(result.get("success"));
        String resultJson = null;
        int resultBytes = 0;
        try {
            Object results = result.get("results");
            if (results != null) {
                String full = MAPPER.writeValueAsString(results);
                resultBytes = full.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                resultJson = resultBytes > maxResultBytes() ? buildTruncatedJson(full, resultBytes) : full;
            }
        } catch (Exception e) {
            log.warn("序列化命令结果失败: {}", e.getMessage());
        }

        ArthasDiagnoseResultModel resultModel = new ArthasDiagnoseResultModel();
        resultModel.setRecordId(record.getId());
        resultModel.setCommand(command);
        resultModel.setCommandType(result.get("commandType") != null ? result.get("commandType").toString() : "unknown");
        resultModel.setResultJson(resultJson);
        resultModel.setResultSizeKb(resultBytes / 1024);
        resultModel.setExecTime(execStart);
        resultModel.setDurationMs(durationMs);
        resultModel.setSuccess(success);
        resultModel.setErrorMsg(!success && result.get("errorMsg") != null ? result.get("errorMsg").toString() : null);
        resultModel.setTenantId(record.getTenantId());
        resultMapper.insert(resultModel);

        // 只刷新最后活跃时间：用轻量语句代替全字段 update，
        // 避免并发命令（如一键体检同时发多条）互相覆盖整行数据。
        try {
            recordMapper.touchUpdatedAt(record.getId(), System.currentTimeMillis());
        } catch (Exception e) {
            log.debug("刷新诊断记录活跃时间失败（不影响诊断结果）: {}", e.getMessage());
        }

        if (reattached) {
            result.put("reattached", Boolean.TRUE);
        }
        return result;
    }

    /**
     * 判断异常是否属于"会话丢了"这一类可自愈错误。
     * 只有这类才值得重连重试：命令非法、参数错误、执行超时等都不该盲目重试。
     */
    private boolean isSessionLost(Throwable e) {
        String msg = rootMessage(e);
        return msg.contains("会话不存在")
                || msg.contains("已结束")
                || msg.contains("目标进程已退出")
                || msg.contains("not attached")
                || msg.contains("Connection refused")
                || msg.contains("连接已断开");
    }

    /** 沿异常链找到最原始的错误信息 */
    private String rootMessage(Throwable e) {
        Throwable t = e;
        for (int i = 0; t.getCause() != null && i < 10; i++) {
            t = t.getCause();
        }
        String msg = t != null ? t.getMessage() : e.getMessage();
        return msg == null ? "" : msg;
    }

    /**
     * 重新 attach 到目标进程并恢复记录状态。
     * 目标进程本身已退出的情况无法自愈，异常会直接冒泡给调用方。
     */
    private void reattach(ArthasDiagnoseRecordModel record) {
        if (record.getPid() == null) {
            throw new RuntimeException("诊断记录缺少目标进程 PID，无法自动重连");
        }
        Map<String, Object> attachResult = agentProxy.attach(
                record.getNodeId(), record.getPid().longValue(), record.getProjectId());
        Object ver = attachResult.get("arthasVersion");
        if (ver != null) {
            record.setArthasVersion(ver.toString());
        }
        record.setStatus("RUNNING");
        record.setUpdatedAt(System.currentTimeMillis());
        recordMapper.update(record);
        log.info("Arthas 会话自动重连成功: sessionId={}, nodeId={}, pid={}",
                record.getSessionId(), record.getNodeId(), record.getPid());
    }

    /**
     * 构造截断后的结果 JSON。
     * 保持结构是合法 JSON，前端可依据 _truncated 字段提示用户结果已精简。
     */
    private String buildTruncatedJson(String fullJson, int originalBytes) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("_truncated", Boolean.TRUE);
        wrapper.put("_originalKb", originalBytes / 1024);
        wrapper.put("_reason", "结果超出 " + (maxResultBytes() / 1024) + "KB，已截断存储；完整产物请通过火焰图/堆转储下载获取");
        wrapper.put("_preview", fullJson.length() > TRUNCATED_PREVIEW_CHARS
                ? fullJson.substring(0, TRUNCATED_PREVIEW_CHARS) : fullJson);
        try {
            return MAPPER.writeValueAsString(wrapper);
        } catch (Exception e) {
            log.warn("构造截断结果失败: {}", e.getMessage());
            return "{\"_truncated\":true}";
        }
    }

    /**
     * 查询会话状态
     */
    public Map<String, Object> getStatus(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }

        // Agent 侧会话可能因超时自动 detach、或目标进程退出而消失，
        // 只信数据库状态会让前端一直显示"已连接"，直到用户执行命令才报错。
        // 这里对 RUNNING 状态的会话做一次真实校验并回写数据库。
        boolean agentReachable = true;
        if ("RUNNING".equals(record.getStatus()) && record.getPid() != null) {
            Map<String, Object> agentStatus = agentProxy.status(record.getNodeId(), record.getPid().longValue());
            if (agentStatus == null) {
                // Agent 不可达（网络/节点问题），无法判定，保持原状态
                agentReachable = false;
            } else if (!Boolean.TRUE.equals(agentStatus.get("attached"))) {
                long now = System.currentTimeMillis();
                record.setStatus("FINISHED");
                record.setEndTime(now);
                record.setDurationMs((int) (now - record.getStartTime()));
                record.setUpdatedAt(now);
                recordMapper.update(record);
                log.info("诊断会话在 Agent 侧已结束，回写状态: sessionId={}", sessionId);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("recordId", record.getId());
        data.put("status", record.getStatus());
        data.put("pid", record.getPid());
        data.put("projectId", record.getProjectId());
        data.put("nodeId", record.getNodeId());
        data.put("arthasVersion", record.getArthasVersion());
        data.put("startTime", record.getStartTime());
        data.put("endTime", record.getEndTime());
        data.put("durationMs", record.getDurationMs());
        data.put("agentReachable", agentReachable);
        return data;
    }

    /**
     * 删除诊断记录及其关联的命令结果
     */
    public void deleteDiagnose(Long id) {
        ArthasDiagnoseRecordModel record = recordMapper.findById(id);
        if (record == null) {
            throw new RuntimeException("诊断记录不存在: id=" + id);
        }
        if (!securityContext.hasProjectPermission(record.getProjectId())) {
            throw new RuntimeException("无权限访问该项目");
        }
        // 先删结果，再删主记录，避免出现无主的结果数据
        resultMapper.deleteByRecordIds(java.util.Collections.singletonList(id));
        recordMapper.deleteByIds(java.util.Collections.singletonList(id));
        log.info("删除诊断记录: id={}, sessionId={}", id, record.getSessionId());
    }

    /**
     * 诊断历史列表
     */
    public Map<String, Object> getHistory(Long projectId, Long nodeId, String status,
                                          Long startTime, Long endTime, int page, int pageSize) {
        if (!securityContext.hasProjectPermission(projectId)) {
            throw new RuntimeException("无权限访问该项目");
        }
        // 页码/页大小防御：前端传 0 或负数时 offset 会算成负数，H2 直接抛语法错误
        int safePage = page > 0 ? page : 1;
        int safePageSize = pageSize > 0 ? Math.min(pageSize, 200) : 20;
        int offset = (safePage - 1) * safePageSize;
        List<ArthasDiagnoseRecordModel> list = recordMapper.findByProjectId(
                projectId, nodeId, status, startTime, endTime, offset, safePageSize);
        int total = recordMapper.countByProjectId(projectId, nodeId, status, startTime, endTime);

        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", safePage);
        data.put("pageSize", safePageSize);
        return data;
    }

    /**
     * 诊断详情
     */
    public Map<String, Object> getDetail(Long id) {
        ArthasDiagnoseRecordModel record = recordMapper.findById(id);
        if (record == null) {
            throw new RuntimeException("诊断记录不存在: id=" + id);
        }
        if (!securityContext.hasProjectPermission(record.getProjectId())) {
            throw new RuntimeException("无权限访问该项目");
        }
        List<ArthasDiagnoseResultModel> results = resultMapper.findByRecordId(id);

        Map<String, Object> data = new HashMap<>();
        data.put("record", record);
        data.put("results", results);
        return data;
    }

    /**
     * 获取火焰图历史文件列表
     */
    public Map<String, Object> getFlamegraphList(String sessionId) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        List<Map<String, Object>> list = agentProxy.flamegraphList(record.getNodeId(), record.getPid());
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("downloadBaseUrl", "/api/arthas/diagnose/flamegraph/download?sessionId=" + sessionId + "&fileName=");
        return data;
    }

    /**
     * 获取火焰图下载 URL
     */
    public String getFlamegraphDownloadUrl(String sessionId, String fileName) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }
        return agentProxy.buildFlamegraphDownloadUrl(record.getNodeId(), record.getPid(), fileName);
    }

    /**
     * 一键诊断（自动分析内存问题）
     * @param sessionId 诊断会话 ID
     * @param diagnoseType 诊断类型：jmap-histo, thread-print, gc-stats
     */
    public Map<String, Object> autoDiagnose(String sessionId, String diagnoseType) {
        ArthasDiagnoseRecordModel record = recordMapper.findBySessionId(sessionId);
        if (record == null) {
            throw new RuntimeException("诊断会话不存在: sessionId=" + sessionId);
        }

        // 调用 Agent 执行诊断
        Map<String, Object> result = agentProxy.diagnose(record.getNodeId(), record.getPid(), diagnoseType);

        // 解析并添加分析建议
        if ("jmap-histo".equals(diagnoseType)) {
            result.put("analysis", analyzeJmapHisto(result));
        }

        return result;
    }

    /**
     * 分析 jmap -histo 结果，给出建议
     */
    private Map<String, Object> analyzeJmapHisto(Map<String, Object> result) {
        Map<String, Object> analysis = new HashMap<>();
        List<String> suggestions = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topClasses = (List<Map<String, Object>>) result.get("topClasses");
        if (topClasses != null && !topClasses.isEmpty()) {
            // 检查常见问题
            for (Map<String, Object> classInfo : topClasses) {
                String className = (String) classInfo.get("className");
                long instances = toLong(classInfo.get("instances"));

                // byte[] 过多
                if (className.contains("byte[]") && instances > 100000) {
                    suggestions.add("byte[] 实例数过多（" + instances + "），可能存在大文件读取或网络缓冲区未释放");
                }

                // HashMap 过多
                if (className.contains("HashMap") && instances > 50000) {
                    suggestions.add("HashMap 实例数过多（" + instances + "），可能存在大量缓存或集合未释放");
                }

                // 字符串过多
                if (className.contains("String") && instances > 100000) {
                    suggestions.add("String 实例数过多（" + instances + "），可能存在字符串拼接或日志过多");
                }
            }

            // 检查总内存
            Long totalBytes = toLong(result.get("totalBytes"));
            if (totalBytes != null && totalBytes > 500 * 1024 * 1024) { // 超过 500MB
                suggestions.add("堆内存占用过高（" + result.get("totalBytesFormatted") + "），建议检查内存泄漏");
            }
        }

        if (suggestions.isEmpty()) {
            suggestions.add("未发现明显异常，内存使用正常");
        }

        analysis.put("suggestions", suggestions);
        analysis.put("hasIssue", suggestions.size() > 1 || !suggestions.get(0).contains("正常"));
        return analysis;
    }

    /**
     * 安全地将对象转换为 long 类型
     */
    private long toLong(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
