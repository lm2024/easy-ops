package com.ops.server.selfheal.controller;

import com.ops.common.model.SelfHealEventModel;
import com.ops.common.model.SelfHealPolicyModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.SelfHealEventMapper;
import com.ops.server.selfheal.service.SelfHealPolicyService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自愈策略与事件 REST 接口
 */
@RestController
@RequestMapping("/self-heal")
public class SelfHealController {

    @Autowired
    private SelfHealPolicyService policyService;

    @Autowired
    private SelfHealEventMapper eventMapper;

    @Autowired
    private SecurityContext securityContext;

    /**
     * GET /api/self-heal/policies - 策略列表
     */
    @GetMapping("/policies")
    public Result<?> listPolicies() {
        List<SelfHealPolicyModel> list = policyService.listAll();
        return Result.success(list);
    }

    /**
     * GET /api/self-heal/policies/{projectId} - 单项目策略
     */
    @GetMapping("/policies/{projectId}")
    public Result<?> getPolicy(@PathVariable Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无项目权限");
        }
        SelfHealPolicyModel policy = policyService.getByProjectId(projectId);
        return Result.success(policy);
    }

    /**
     * POST /api/self-heal/policies - 创建/更新策略
     */
    @PostMapping("/policies")
    public Result<?> savePolicy(@RequestBody SelfHealPolicyModel policy) {
        if (policy.getProjectId() != null && !securityContext.hasProjectPermission(policy.getProjectId())) {
            return Result.error(403, "无项目权限");
        }
        SelfHealPolicyModel saved = policyService.save(policy);
        return Result.success(saved);
    }

    /**
     * POST /api/self-heal/policies/{projectId}/circuit-break - 解除熔断
     */
    @PostMapping("/policies/{projectId}/circuit-break")
    public Result<?> resetCircuitBreaker(@PathVariable Long projectId) {
        if (!securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无项目权限");
        }
        SelfHealPolicyModel policy = policyService.resetCircuitBreaker(projectId);
        return Result.success(policy);
    }

    /**
     * GET /api/self-heal/events - 自愈事件历史
     */
    @GetMapping("/events")
    public Result<?> listEvents(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        if (projectId != null && !securityContext.hasProjectPermission(projectId)) {
            return Result.error(403, "无项目权限");
        }
        List<SelfHealEventModel> list = eventMapper.findByFilters(projectId, page, pageSize);
        Long total = eventMapper.countByFilters(projectId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", total);
        return Result.success(data);
    }
}
