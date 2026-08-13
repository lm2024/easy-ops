package com.ops.server.traffic.controller;

import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.NginxSourceWhitelistModel;
import com.ops.common.model.NginxTrafficAlarmRuleModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.service.TenantResourceAccessService;
import com.ops.server.traffic.service.NginxTrafficAlarmService;
import com.ops.server.traffic.service.NginxTrafficService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * Nginx 流量监控接口
 */
@RestController
@RequestMapping("/nginx-traffic")
public class NginxTrafficController {

    @Autowired
    private NginxTrafficService nginxTrafficService;
    @Autowired
    private NginxTrafficAlarmService nginxTrafficAlarmService;
    @Autowired
    private com.ops.server.traffic.service.NginxSourceWhitelistService whitelistService;
    @Autowired
    private NodeMapper nodeMapper;
    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;
    @Autowired
    private SecurityContext securityContext;

    @GetMapping("/sources")
    public Result<?> listSources() {
        return Result.success(nginxTrafficService.listSources());
    }

    @PostMapping("/sources")
    public Result<?> saveSource(@RequestBody NginxAccessSourceModel model) {
        if (model.getId() != null) {
            NginxAccessSourceModel existing = tenantResourceAccessService.requireSource(model.getId());
            model.setTenantId(existing.getTenantId());
        } else {
            // 创建日志源：先校验节点归属，再物化当前租户（null 时服务层回退到 node 租户）
            tenantResourceAccessService.requireNode(model.getNodeId());
            model.setTenantId(securityContext.getCurrentTenantId());
        }
        return Result.success(nginxTrafficService.saveSource(model));
    }

    @DeleteMapping("/sources/{id}")
    public Result<?> deleteSource(@PathVariable Long id) {
        tenantResourceAccessService.requireSource(id);
        nginxTrafficService.deleteSource(id);
        return Result.success(null);
    }

    @GetMapping("/sources/{id}/alarm-rules")
    public Result<?> listAlarmRules(@PathVariable Long id) {
        tenantResourceAccessService.requireSource(id);
        return Result.success(nginxTrafficAlarmService.listBySourceId(id));
    }

    @PutMapping("/sources/{id}/alarm-rules")
    public Result<?> saveAlarmRules(@PathVariable Long id,
                                    @RequestBody List<NginxTrafficAlarmRuleModel> rules) {
        NginxAccessSourceModel source = tenantResourceAccessService.requireSource(id);
        return Result.success(nginxTrafficAlarmService.saveRules(id, resolveTenantId(source), rules));
    }

    @GetMapping("/sources/{id}/whitelist")
    public Result<?> listWhitelist(@PathVariable Long id) {
        tenantResourceAccessService.requireSource(id);
        return Result.success(whitelistService.listBySource(id));
    }

    @PutMapping("/sources/{id}/whitelist")
    public Result<?> saveWhitelist(@PathVariable Long id,
                                   @RequestBody List<NginxSourceWhitelistModel> items) {
        NginxAccessSourceModel source = tenantResourceAccessService.requireSource(id);
        return Result.success(whitelistService.saveAll(id, resolveTenantId(source), items));
    }

    /**
     * Agent 拉取本节点启用的日志源
     */
    @GetMapping("/agent/sources")
    public Result<?> agentSources(HttpServletRequest request) {
        Long nodeId = resolveNodeId(request);
        if (nodeId == null) {
            return Result.error(401, "节点未认证");
        }
        return Result.success(nginxTrafficService.listAgentSources(nodeId));
    }

    /**
     * Agent 上报分钟汇总
     */
    @PostMapping("/ingest")
    public Result<?> ingest(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long nodeId = resolveNodeId(request);
        if (nodeId == null) {
            return Result.error(401, "节点未认证");
        }
        Long sourceId = toLong(body.get("sourceId"));
        if (sourceId == null) {
            return Result.error(400, "sourceId 不能为空");
        }
        // 防 Agent 伪造/越权 ingest：先校验 source 归属；服务层再校验 source 必须属于当前节点
        tenantResourceAccessService.requireSource(sourceId);
        nginxTrafficService.ingest(nodeId, sourceId, body);
        return Result.success(null);
    }

    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(required = false) List<Long> sourceIds,
                              @RequestParam(defaultValue = "60") Integer windowMinutes,
                              @RequestParam(required = false) Long startTime,
                              @RequestParam(required = false) Long endTime) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.overview(sourceIds, windowMinutes, startTime, endTime));
    }

    @GetMapping("/overview/today")
    public Result<?> todayOverview(@RequestParam(required = false) List<Long> sourceIds) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.todayOverview(sourceIds));
    }

    @GetMapping("/rank/ip")
    public Result<?> rankIp(@RequestParam(required = false) List<Long> sourceIds,
                            @RequestParam(defaultValue = "60") Integer windowMinutes,
                            @RequestParam(required = false) Long startTime,
                            @RequestParam(required = false) Long endTime,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer pageSize,
                            @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankIp(sourceIds, windowMinutes, startTime, endTime, keyword, page, pageSize, sort));
    }

    @GetMapping("/rank/uri")
    public Result<?> rankUri(@RequestParam(required = false) List<Long> sourceIds,
                             @RequestParam(defaultValue = "60") Integer windowMinutes,
                             @RequestParam(required = false) Long startTime,
                             @RequestParam(required = false) Long endTime,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "20") Integer pageSize,
                             @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankUri(sourceIds, windowMinutes, startTime, endTime, keyword, page, pageSize, sort));
    }

    @GetMapping("/rank/ip-uri")
    public Result<?> rankIpUri(@RequestParam(required = false) List<Long> sourceIds,
                               @RequestParam(defaultValue = "60") Integer windowMinutes,
                               @RequestParam(required = false) Long startTime,
                               @RequestParam(required = false) Long endTime,
                               @RequestParam(required = false) String clientIp,
                               @RequestParam(required = false) String uri,
                               @RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "20") Integer pageSize,
                               @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankIpUri(sourceIds, windowMinutes, startTime, endTime, clientIp, uri, page, pageSize, sort));
    }

    @GetMapping("/rank/slow")
    public Result<?> rankSlow(@RequestParam(required = false) List<Long> sourceIds,
                              @RequestParam(defaultValue = "60") Integer windowMinutes,
                              @RequestParam(required = false) Long startTime,
                              @RequestParam(required = false) Long endTime,
                              @RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "20") Integer pageSize,
                              @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankSlow(sourceIds, windowMinutes, startTime, endTime, page, pageSize, sort));
    }

    @GetMapping("/rank/method")
    public Result<?> rankMethod(@RequestParam(required = false) List<Long> sourceIds,
                                @RequestParam(defaultValue = "60") Integer windowMinutes,
                                @RequestParam(required = false) Long startTime,
                                @RequestParam(required = false) Long endTime,
                                @RequestParam(defaultValue = "1") Integer page,
                                @RequestParam(defaultValue = "20") Integer pageSize,
                                @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankMethod(sourceIds, windowMinutes, startTime, endTime, page, pageSize, sort));
    }

    @GetMapping("/rank/ua")
    public Result<?> rankUa(@RequestParam(required = false) List<Long> sourceIds,
                            @RequestParam(defaultValue = "60") Integer windowMinutes,
                            @RequestParam(required = false) Long startTime,
                            @RequestParam(required = false) Long endTime,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer pageSize,
                            @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankUa(sourceIds, windowMinutes, startTime, endTime, keyword, page, pageSize, sort));
    }

    @GetMapping("/rank/referer")
    public Result<?> rankReferer(@RequestParam(required = false) List<Long> sourceIds,
                                 @RequestParam(defaultValue = "60") Integer windowMinutes,
                                 @RequestParam(required = false) Long startTime,
                                 @RequestParam(required = false) Long endTime,
                                 @RequestParam(required = false) String keyword,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "20") Integer pageSize,
                                 @RequestParam(required = false) String sort) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.rankReferer(sourceIds, windowMinutes, startTime, endTime, keyword, page, pageSize, sort));
    }

    @GetMapping("/latency/samples")
    public Result<?> latencySamples(@RequestParam(required = false) List<Long> sourceIds,
                                    @RequestParam(defaultValue = "60") Integer windowMinutes,
                                    @RequestParam(required = false) Long startTime,
                                    @RequestParam(required = false) Long endTime,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "50") Integer pageSize) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.latencySamples(sourceIds, windowMinutes, startTime, endTime, page, pageSize));
    }

    @GetMapping("/trend")
    public Result<?> trend(@RequestParam(required = false) List<Long> sourceIds,
                           @RequestParam(defaultValue = "60") Integer windowMinutes,
                           @RequestParam(required = false) Long startTime,
                           @RequestParam(required = false) Long endTime) {
        requireSources(sourceIds);
        return Result.success(nginxTrafficService.trend(sourceIds, windowMinutes, startTime, endTime));
    }

    /** 校验统计/查询入参中的每个 sourceId 均为当前租户可访问的日志源 */
    private void requireSources(List<Long> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return;
        }
        for (Long sourceId : sourceIds) {
            if (sourceId != null) {
                tenantResourceAccessService.requireSource(sourceId);
            }
        }
    }

    /** 日志源 tenant_id 可能为 null（历史数据/平台源），统一归 0 以便 tenant 过滤命中 */
    private Long resolveTenantId(NginxAccessSourceModel source) {
        return source != null && source.getTenantId() != null ? source.getTenantId() : 0L;
    }

    private Long resolveNodeId(HttpServletRequest request) {
        Object nodeIdAttr = request.getAttribute(AuthInterceptor.ATTR_NODE_ID);
        if (nodeIdAttr != null) {
            return Long.parseLong(String.valueOf(nodeIdAttr));
        }
        String token = request.getHeader("X-Token");
        if (token == null || token.isEmpty()) {
            return null;
        }
        String nodeIdStr = nodeMapper.getNodeIdByToken(token);
        if (nodeIdStr == null || nodeIdStr.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(nodeIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
