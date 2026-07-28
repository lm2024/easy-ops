package com.ops.server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定服务（纯内存实现，重启自动解锁）
 *
 * 规则：
 *   - 同一账号 5 次失败 → 锁定 15 分钟
 *   - 锁定期间返回剩余锁定时间
 *   - 登录成功后清除失败记录
 *   - 定期清理过期记录（5分钟无活动）
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_FAIL_COUNT = 5;
    private static final long LOCK_DURATION_MS = 15 * 60 * 1000; // 15 分钟
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

    private final Map<String, AttemptRecord> records = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    /**
     * 检查账号是否被锁定。
     * @return null 表示可正常登录；否则返回锁定提示信息
     */
    public String checkLocked(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        cleanupIfNeeded();
        String key = normalize(username);
        AttemptRecord record = records.get(key);
        if (record == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (record.lockedUntil > 0 && now < record.lockedUntil) {
            long remainMinutes = (record.lockedUntil - now) / 60_000 + 1;
            return "账号已被锁定，请 " + remainMinutes + " 分钟后重试";
        }
        // 锁定已过期，自动清除
        if (record.lockedUntil > 0 && now >= record.lockedUntil) {
            records.remove(key);
        }
        return null;
    }

    /**
     * 登录失败时调用。
     */
    public void onFailure(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        String key = normalize(username);
        long now = System.currentTimeMillis();
        AttemptRecord record = records.computeIfAbsent(key, k -> new AttemptRecord());
        record.failCount++;
        record.lastAttemptTime = now;
        if (record.failCount >= MAX_FAIL_COUNT) {
            record.lockedUntil = now + LOCK_DURATION_MS;
            log.warn("[SECURITY] Account locked: username='{}', failCount={}, lockedUntil={}",
                    key, record.failCount, record.lockedUntil);
        }
    }

    /**
     * 登录成功时调用。
     */
    public void onSuccess(String username) {
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        String key = normalize(username);
        AttemptRecord removed = records.remove(key);
        if (removed != null && removed.failCount > 0) {
            log.info("[SECURITY] Account unlocked after successful login: username='{}'", key);
        }
    }

    private String normalize(String username) {
        return username.trim().toLowerCase();
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanup = now;
        int before = records.size();
        records.entrySet().removeIf(entry -> {
            AttemptRecord r = entry.getValue();
            return r.lockedUntil > 0 && now >= r.lockedUntil + CLEANUP_INTERVAL_MS;
        });
        int after = records.size();
        if (before != after) {
            log.debug("[LoginAttempt] Cleaned up {} expired records, remaining: {}", before - after, after);
        }
    }

    private static class AttemptRecord {
        int failCount;
        long lockedUntil;
        long lastAttemptTime;
    }
}
