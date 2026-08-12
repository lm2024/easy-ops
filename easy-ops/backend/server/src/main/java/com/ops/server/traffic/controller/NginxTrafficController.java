package com.ops.server.traffic.controller;

import com.ops.common.model.NginxAccessSourceModel;
import com.ops.common.model.NginxSourceWhitelistModel;
import com.ops.common.model.NginxTrafficAlarmRuleModel;
import com.ops.common.response.Result;
import com.ops.server.interceptor.AuthInterceptor;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.traffic.service.NginxTrafficAlarmService;
import com.ops.server.traffic.service.NginxTrafficService;
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

    @GetMapping("/sources")
    public Result<?> listSources() {
        return Result.success(nginxTrafficService.listSources());
    }

    @PostMapping("/sources")
    public Result<?> saveSource(@RequestBody NginxAccessSourceModel model) {
        return Result.success(nginxTrafficService.saveSource(model));
    }

    @DeleteMapping("/sources/{id}")
    public Result<?> deleteSource(@PathVariable Long id) {
        nginxTrafficService.deleteSource(id);
        return Result.success(null);
    }

    @GetMapping("/sources/{id}/alarm-rules")
    public Result<?> listAlarmRules(@PathVariable Long id) {
        nginxTrafficService.assertSourceAccess(id);
        return Result.success(nginxTrafficAlarmService.listBySourceId(id));
    }

    @PutMapping("/sources/{id}/alarm-rules")
    public Result<?> saveAlarmRules(@PathVariable Long id,
                                    @RequestBody List<NginxTrafficAlarmRuleModel> rules) {
        nginxTrafficService.assertSourceAccess(id);
        return Result.success(nginxTrafficAlarmService.saveRules(id, rules));
    }

    @GetMapping("/sources/{id}/whitelist")
    public Result<?> listWhitelist(@PathVariable Long id) {
        nginxTrafficService.assertSourceAccess(id);
        return Result.success(whitelistService.listBySource(id));
    }

    @PutMapping("/sources/{id}/whitelist")
    public Result<?> saveWhitelist(@PathVariable Long id,
                                   @RequestBody List<NginxSourceWhitelistModel> items) {
        nginxTrafficService.assertSourceAccess(id);
        return Result.success(whitelistService.saveAll(id, items));
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
        nginxTrafficService.ingest(nodeId, sourceId, body);
        return Result.success(null);
    }

    @GetMapping("/overview")
    public Result<?> overview(@RequestParam(required = false) List<Long> sourceIds,
                              @RequestParam(defaultValue = "60") Integer windowMinutes,
                              @RequestParam(required = false) Long startTime,
                              @RequestParam(required = false) Long endTime) {
        return Result.success(nginxTrafficService.overview(sourceIds, windowMinutes, startTime, endTime));
    }

    @GetMapping("/overview/today")
    public Result<?> todayOverview(@RequestParam(required = false) List<Long> sourceIds) {
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
        return Result.success(nginxTrafficService.rankReferer(sourceIds, windowMinutes, startTime, endTime, keyword, page, pageSize, sort));
    }

    @GetMapping("/latency/samples")
    public Result<?> latencySamples(@RequestParam(required = false) List<Long> sourceIds,
                                    @RequestParam(defaultValue = "60") Integer windowMinutes,
                                    @RequestParam(required = false) Long startTime,
                                    @RequestParam(required = false) Long endTime,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "50") Integer pageSize) {
        return Result.success(nginxTrafficService.latencySamples(sourceIds, windowMinutes, startTime, endTime, page, pageSize));
    }

    @GetMapping("/trend")
    public Result<?> trend(@RequestParam(required = false) List<Long> sourceIds,
                           @RequestParam(defaultValue = "60") Integer windowMinutes,
                           @RequestParam(required = false) Long startTime,
                           @RequestParam(required = false) Long endTime) {
        return Result.success(nginxTrafficService.trend(sourceIds, windowMinutes, startTime, endTime));
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
