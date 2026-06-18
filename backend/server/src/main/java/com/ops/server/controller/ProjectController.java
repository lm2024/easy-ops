package com.ops.server.controller;

import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    @Autowired
    private ProjectMapper projectMapper;

    /**
     * GET /api/projects - 项目列表
     */
    @GetMapping
    public Result<?> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long nodeId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<ProjectModel> projects = projectMapper.findByFilters(status, nodeId, page, pageSize);
        Long total = projectMapper.countByFilters(status, nodeId);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", projects);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/projects/{id} - 项目详情
     */
    @GetMapping("/{id}")
    public Result<?> getProject(@PathVariable Long id) {
        ProjectModel project = projectMapper.findById(id);
        return project != null ? Result.success(project) : Result.error(1005, "项目不存在");
    }

    /**
     * POST /api/projects - 创建项目
     */
    @PostMapping
    public Result<?> createProject(@RequestBody ProjectModel project) {
        if (projectMapper.findByName(project.getName()) != null) {
            return Result.paramError("项目名称已存在");
        }
        project.setStatus(1);
        project.setCreateTime(System.currentTimeMillis());
        project.setUpdateTime(System.currentTimeMillis());
        projectMapper.insert(project);
        return Result.success();
    }

    /**
     * PUT /api/projects/{id} - 修改项目
     */
    @PutMapping("/{id}")
    public Result<?> updateProject(@PathVariable Long id, @RequestBody ProjectModel project) {
        ProjectModel existing = projectMapper.findById(id);
        if (existing == null) {
            return Result.error(1005, "项目不存在");
        }
        project.setId(id);
        project.setCreateTime(existing.getCreateTime());
        project.setUpdateTime(System.currentTimeMillis());
        projectMapper.update(project);
        return Result.success();
    }

    /**
     * DELETE /api/projects/{id} - 删除项目
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteProject(@PathVariable Long id) {
        projectMapper.deleteById(id);
        return Result.success();
    }
}
