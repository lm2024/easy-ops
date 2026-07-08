package com.ops.server.controller;

import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.service.ProjectService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private SecurityContext securityContext;

    /**
     * GET /api/projects - 项目列表 (SEC-004: 增加用户项目范围过滤)
     */
    @GetMapping
    public Result<?> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<ProjectModel> projects = projectService.findByFilters(status, nodeId, page, pageSize);
        Long total = projectService.countByFilters(status, nodeId);
        Map<String, Object> data = new HashMap<>();
        data.put("list", projects);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/projects/{id} - 项目详情 (SEC-004: 增加权限校验)
     */
    @GetMapping("/{id}")
    public Result<?> getProject(@PathVariable Long id) {
        if (!securityContext.hasProjectPermission(id)) {
            return Result.error(403, "无权访问该项目");
        }
        ProjectModel project = projectService.findById(id);
        return project != null ? Result.success(project) : Result.error(1005, "项目不存在");
    }

    /**
     * POST /api/projects - 创建项目
     */
    @PostMapping
    public Result<?> createProject(@RequestBody ProjectModel project) {
        if (projectService.findByName(project.getName()) != null) {
            return Result.paramError("项目名称已存在");
        }
        // 自动修正 startScript 中 JAR_NAME 与 jarName 不一致的问题
        fixScriptJarName(project);
        project.setStatus(1);
        project.setCreateTime(System.currentTimeMillis());
        project.setUpdateTime(System.currentTimeMillis());
        projectService.insert(project);
        return Result.success();
    }

    /**
     * PUT /api/projects/{id} - 修改项目 (SEC-004: 增加权限校验)
     */
    @PutMapping("/{id}")
    public Result<?> updateProject(@PathVariable Long id, @RequestBody ProjectModel project) {
        if (!securityContext.hasProjectPermission(id)) {
            return Result.error(403, "无权修改该项目");
        }
        ProjectModel existing = projectService.findById(id);
        if (existing == null) {
            return Result.error(1005, "项目不存在");
        }
        // 自动修正 startScript 中 JAR_NAME 与 jarName 不一致的问题
        fixScriptJarName(project);
        project.setId(id);
        project.setCreateTime(existing.getCreateTime());
        project.setUpdateTime(System.currentTimeMillis());
        projectService.update(project);
        return Result.success();
    }

    /** 自动修正 startScript 中 JAR_NAME=xxx 使其与项目 jarName 一致 */
    private void fixScriptJarName(ProjectModel project) {
        String jarName = project.getJarName();
        String startScript = project.getStartScript();
        if (jarName != null && !jarName.isEmpty() && startScript != null && !startScript.isEmpty()) {
            // 用正则替换 JAR_NAME=旧值 → JAR_NAME=新值
            String fixed = startScript.replaceAll("JAR_NAME=\\S+", "JAR_NAME=" + jarName);
            if (!fixed.equals(startScript)) {
                project.setStartScript(fixed);
            }
        }
    }

    /**
     * DELETE /api/projects/{id} - 删除项目 (SEC-004: 增加权限校验)
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteProject(@PathVariable Long id) {
        if (!securityContext.hasProjectPermission(id)) {
            return Result.error(403, "无权删除该项目");
        }
        projectService.deleteById(id);
        return Result.success();
    }
}
