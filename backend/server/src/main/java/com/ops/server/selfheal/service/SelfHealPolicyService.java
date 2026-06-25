package com.ops.server.selfheal.service;

import com.ops.common.model.SelfHealPolicyModel;
import com.ops.server.mapper.SelfHealPolicyMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 自愈策略 CRUD 服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class SelfHealPolicyService {

    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final int DEFAULT_RETRY_INTERVAL = 30;
    private static final int DEFAULT_CHECK_INTERVAL = 30;

    @Autowired
    private SelfHealPolicyMapper policyMapper;

    /**
     * 查询全部策略
     */
    public List<SelfHealPolicyModel> listAll() {
        return policyMapper.findAll();
    }

    /**
     * 按项目查询策略
     */
    public SelfHealPolicyModel getByProjectId(Long projectId) {
        return policyMapper.findByProjectId(projectId);
    }

    /**
     * 查询已启用策略
     */
    public List<SelfHealPolicyModel> listEnabled() {
        return policyMapper.findEnabled();
    }

    /**
     * 创建或更新策略（按 projectId upsert）
     */
    public SelfHealPolicyModel save(SelfHealPolicyModel policy) {
        if (policy.getProjectId() == null) {
            throw new IllegalArgumentException("projectId 不能为空");
        }
        long now = System.currentTimeMillis();
        SelfHealPolicyModel existing = policyMapper.findByProjectId(policy.getProjectId());
        applyDefaults(policy);
        if (existing == null) {
            policy.setCircuitBreaker(0);
            policy.setCreateTime(now);
            policy.setUpdateTime(now);
            policyMapper.insert(policy);
        } else {
            policy.setUpdateTime(now);
            policyMapper.update(policy);
            policy.setId(existing.getId());
            policy.setCircuitBreaker(existing.getCircuitBreaker());
            policy.setCircuitBreakTime(existing.getCircuitBreakTime());
        }
        return policyMapper.findByProjectId(policy.getProjectId());
    }

    /**
     * 解除熔断
     */
    public SelfHealPolicyModel resetCircuitBreaker(Long projectId) {
        long now = System.currentTimeMillis();
        policyMapper.updateCircuitBreaker(projectId, 0, null, now);
        return policyMapper.findByProjectId(projectId);
    }

    private void applyDefaults(SelfHealPolicyModel policy) {
        if (policy.getEnabled() == null) {
            policy.setEnabled(1);
        }
        if (policy.getMaxRetries() == null) {
            policy.setMaxRetries(DEFAULT_MAX_RETRIES);
        }
        if (policy.getRetryIntervalSec() == null) {
            policy.setRetryIntervalSec(DEFAULT_RETRY_INTERVAL);
        }
        if (policy.getCheckIntervalSec() == null) {
            policy.setCheckIntervalSec(DEFAULT_CHECK_INTERVAL);
        }
        if (policy.getNotifyEmail() == null) {
            policy.setNotifyEmail(1);
        }
        if (policy.getNotifyPopup() == null) {
            policy.setNotifyPopup(1);
        }
        if (policy.getAutoAiDiagnose() == null) {
            policy.setAutoAiDiagnose(0);
        }
    }
}
