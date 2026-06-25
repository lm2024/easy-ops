package com.ops.server.selfheal.scheduler;

import com.ops.common.model.SelfHealPolicyModel;
import com.ops.server.mapper.SysConfigMapper;
import com.ops.server.scheduler.DistributedLock;
import com.ops.server.selfheal.service.SelfHealPolicyService;
import com.ops.server.selfheal.service.SelfHealService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 自愈定时检测调度器
 */
@Component
public class SelfHealScheduler {

    private static final Logger log = LoggerFactory.getLogger(SelfHealScheduler.class);
    private static final String LOCK_NAME = "self_heal_scheduler";
    private static final String CONFIG_KEY_ENABLED = "self_heal.enabled";

    @Autowired
    private DistributedLock distributedLock;

    @Autowired
    private SelfHealPolicyService policyService;

    @Autowired
    private SelfHealService selfHealService;

    @Autowired
    private SysConfigMapper sysConfigMapper;

    @Scheduled(fixedRate = 30000)
    public void runCheck() {
        if (!isEnabled()) {
            return;
        }
        if (!distributedLock.tryLock(LOCK_NAME)) {
            log.debug("SelfHealScheduler: lock not acquired, skipping");
            return;
        }
        try {
            List<SelfHealPolicyModel> policies = policyService.listEnabled();
            for (SelfHealPolicyModel policy : policies) {
                try {
                    selfHealService.checkPolicy(policy);
                } catch (Exception e) {
                    log.error("Self-heal check failed for project {}: {}", policy.getProjectId(), e.getMessage());
                }
            }
        } finally {
            distributedLock.releaseLock(LOCK_NAME);
        }
    }

    private boolean isEnabled() {
        String value = sysConfigMapper.getValue(CONFIG_KEY_ENABLED);
        if (value == null || value.isEmpty()) {
            return true;
        }
        return !"false".equalsIgnoreCase(value) && !"0".equals(value);
    }
}
