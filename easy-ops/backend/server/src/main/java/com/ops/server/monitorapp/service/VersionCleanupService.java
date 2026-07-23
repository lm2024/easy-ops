package com.ops.server.monitorapp.service;

import com.ops.common.model.NodeModel;
import com.ops.common.model.VersionModel;
import com.ops.server.client.AgentClient;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.VersionPackageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 版本包清理服务
 * - 自动清理：上传后保留最新 N 个版本
 * - 手动删除：删除 Server + 所有 Agent 上的版本文件
 * - Agent 通知：并发通知所有 Agent 删除，失败不阻塞
 */
@Service
public class VersionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(VersionCleanupService.class);

    /** 默认保留版本数 */
    private static final int DEFAULT_KEEP_COUNT = 3;

    /** Agent 删除线程池 */
    private static final ExecutorService AGENT_DELETE_POOL = new ThreadPoolExecutor(
            0, 16, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(),
            r -> { Thread t = new Thread(r, "agent-version-delete"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Autowired
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private AgentClient agentClient;

    @Value("${server.path:./data}")
    private String serverPath;

    /**
     * 自动清理旧版本（保留最新 keepCount 个）
     * @return 被清理的版本列表
     */
    public List<VersionModel> autoCleanup(Long projectId, int keepCount) {
        if (keepCount <= 0) keepCount = DEFAULT_KEEP_COUNT;
        List<VersionModel> oldVersions = versionPackageMapper.findOldVersions(projectId, keepCount);
        if (oldVersions == null || oldVersions.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("[VersionCleanup] 项目 {} 需清理 {} 个旧版本，保留最新 {} 个", projectId, oldVersions.size(), keepCount);

        List<VersionModel> cleaned = new ArrayList<>();
        for (VersionModel version : oldVersions) {
            try {
                deleteVersionCompletely(version);
                cleaned.add(version);
            } catch (Exception e) {
                log.warn("[VersionCleanup] 删除版本 {} 失败: {}", version.getVersion(), e.getMessage());
            }
        }
        return cleaned;
    }

    /**
     * 自动清理（使用默认保留数）
     */
    public List<VersionModel> autoCleanup(Long projectId) {
        return autoCleanup(projectId, DEFAULT_KEEP_COUNT);
    }

    /**
     * 手动删除版本（Server + 所有 Agent）
     */
    public void deleteVersion(Long versionId) {
        VersionModel version = versionPackageMapper.findById(versionId);
        if (version == null) {
            throw new IllegalArgumentException("版本不存在: " + versionId);
        }
        deleteVersionCompletely(version);
    }

    /**
     * 完整删除版本：Server 文件 + DB 记录 + 并发通知 Agent
     */
    private void deleteVersionCompletely(VersionModel version) {
        Long projectId = version.getProjectId();
        String versionName = version.getVersion();

        // 1. 删除 Server 端文件
        deleteServerFile(version);

        // 2. 并发通知所有 Agent 删除（失败不阻塞）
        notifyAgentsDelete(projectId, versionName);

        // 3. 删除数据库记录
        versionPackageMapper.deleteById(version.getId());
        log.info("[VersionCleanup] 已删除版本: {} (ID={})", versionName, version.getId());
    }

    /**
     * 删除 Server 端版本目录
     */
    private void deleteServerFile(VersionModel version) {
        String filePath = version.getFilePath();
        if (filePath == null || filePath.isEmpty()) return;

        File dir = new File(filePath);
        if (!dir.isAbsolute()) {
            dir = new File(serverPath, filePath);
        }

        if (dir.exists()) {
            if (dir.isDirectory()) {
                deleteRecursively(dir);
                log.info("[VersionCleanup] 已删除 Server 目录: {}", dir.getAbsolutePath());
            } else {
                dir.delete();
                log.info("[VersionCleanup] 已删除 Server 文件: {}", dir.getAbsolutePath());
            }
        }
    }

    /**
     * 并发通知所有 Agent 删除版本文件
     * 使用线程池并发执行，单个 Agent 失败不影响其他
     */
    private void notifyAgentsDelete(Long projectId, String versionName) {
        List<NodeModel> nodes = nodeMapper.findByStatus(null, 1, 1000, null);
        if (nodes == null || nodes.isEmpty()) return;

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (NodeModel node : nodes) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    Map<String, String> params = new HashMap<>();
                    params.put("projectId", String.valueOf(projectId));
                    params.put("versionName", versionName);
                    Map<String, Object> result = agentClient.delete(node, "/file/version", params);
                    if (result != null) {
                        log.info("[VersionCleanup] Agent {} 删除版本 {} 完成", node.getName(), versionName);
                    } else {
                        log.warn("[VersionCleanup] Agent {} 无响应（可能离线）", node.getName());
                    }
                } catch (Exception e) {
                    log.warn("[VersionCleanup] Agent {} 删除版本失败: {}", node.getName(), e.getMessage());
                }
            }, AGENT_DELETE_POOL);
            futures.add(future);
        }

        // 等待所有 Agent 完成（最多 30 秒）
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("[VersionCleanup] 等待 Agent 删除超时（30s），继续执行");
        } catch (Exception e) {
            log.warn("[VersionCleanup] 等待 Agent 删除异常: {}", e.getMessage());
        }
    }

    /**
     * 递归删除目录
     */
    private void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
