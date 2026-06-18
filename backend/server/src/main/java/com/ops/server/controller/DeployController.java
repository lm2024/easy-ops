package com.ops.server.controller;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.VersionModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.*;
import com.ops.server.service.AlarmService;
import com.ops.server.websocket.DeployHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/deploy")
public class DeployController {

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private AlarmService alarmService;

    @Autowired
    private DeployHandler deployHandler;

    @Value("${server.path:./data}")
    private String serverPath;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * POST /api/deploy - 发布版本
     */
    @PostMapping
    public Result<?> publish(@RequestBody Map<String, Long> request) {
        Long projectId = request.get("projectId");
        Long versionId = request.get("versionId");

        if (projectId == null || versionId == null) {
            return Result.paramError("项目和版本ID不能为空");
        }

        VersionModel version = versionPackageMapper.findById(versionId);
        if (version == null) {
            return Result.error(1004, "版本不存在");
        }

        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }

        String[] nodeIdStrs = project.getNodeIds().split(",");
        String deployId = UUID.randomUUID().toString().substring(0, 8);

        // Create deploy record
        DeployModel deploy = new DeployModel();
        deploy.setProjectId(projectId);
        deploy.setVersionId(versionId);
        deploy.setStatus(DeployStatus.PROCESSING.getCode());
        deploy.setJarName(version.getJarName());
        deploy.setStartTime(System.currentTimeMillis());
        deploy.setCreateTime(System.currentTimeMillis());
        deployRecordMapper.insert(deploy);

        Map<String, Object> data = new HashMap<>();
        data.put("deployId", deployId);
        data.put("recordId", deploy.getId() != null ? deploy.getId() : 0);
        return Result.success(data);
    }

    /**
     * GET /api/deploy - 部署记录列表
     */
    @GetMapping
    public Result<?> listRecords(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<DeployModel> records = deployRecordMapper.findByProjectId(projectId, page, pageSize);
        Long total = deployRecordMapper.countByProjectId(projectId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", records);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * POST /api/deploy/{id}/rollback - 回滚部署
     */
    @PostMapping("/{id}/rollback")
    public Result<?> rollback(@PathVariable Long id) {
        DeployModel record = deployRecordMapper.findById(id);
        if (record == null) {
            return Result.error(500, "部署记录不存在");
        }

        // Create rollback record
        DeployModel rollbackRecord = new DeployModel();
        rollbackRecord.setProjectId(record.getProjectId());
        rollbackRecord.setVersionId(record.getVersionId());
        rollbackRecord.setNodeId(record.getNodeId());
        rollbackRecord.setStatus(DeployStatus.ROLLBACK.getCode());
        rollbackRecord.setJarName(record.getJarName());
        rollbackRecord.setLog("回滚: 回滚到版本ID " + record.getVersionId());
        rollbackRecord.setStartTime(System.currentTimeMillis());
        rollbackRecord.setCreateTime(System.currentTimeMillis());
        deployRecordMapper.insert(rollbackRecord);

        return Result.success();
    }

    /**
     * POST /api/deploy/proxy/receive - 代理Agent文件接收
     */
    @PostMapping("/proxy/receive")
    public Result<?> proxyReceive(@RequestBody Map<String, String> request) {
        String serverUrl = request.get("serverUrl");
        String nodeId = request.get("nodeId");

        try {
            String url = serverUrl + "/api/files/receive";
            Map<String, String> headers = new HashMap<>();
            headers.put("X-Token", "proxy-token");
            String body = restTemplate.postForObject(url, null, String.class);
            return Result.success(body);
        } catch (Exception e) {
            return Result.serverError();
        }
    }

    // Helper: deploy result tracking
    private final Map<String, String> deployStatusTracker = new ConcurrentHashMap<>();

    public Map<String, String> getDeployStatusTracker() {
        return deployStatusTracker;
    }
}
