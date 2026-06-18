package com.ops.server.controller;

import com.ops.common.model.VersionModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.VersionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/versions")
public class VersionController {

    @Autowired
    private VersionMapper versionMapper;

    @Value("${server.path:./data}")
    private String serverPath;

    /**
     * GET /api/versions - 版本列表
     */
    @GetMapping
    public Result<?> listVersions(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
        List<VersionModel> versions = versionMapper.findByProjectId(projectId, page, pageSize);
        Long total = versionMapper.countByProjectId(projectId);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("list", versions);
        data.put("total", total);
        return Result.success(data);
    }

    /**
     * GET /api/versions/{id} - 版本详情
     */
    @GetMapping("/{id}")
    public Result<?> getVersion(@PathVariable Long id) {
        VersionModel version = versionMapper.findById(id);
        return version != null ? Result.success(version) : Result.error(1004, "版本不存在");
    }

    /**
     * POST /api/versions/upload - 上传Jar包
     */
    @PostMapping("/upload")
    public Result<?> uploadVersion(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam(required = false) String remark) throws Exception {
        if (file == null || file.isEmpty()) {
            return Result.paramError("请选择文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.endsWith(".jar")) {
            return Result.error(1006, "仅支持.jar文件");
        }

        // Calculate SHA-256
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = file.getBytes();
        String sha256Str = bytesToHex(sha256.digest(fileBytes));

        // Get project name
        String projectName = versionMapper.getProjectNameByProjectId(projectId);
        if (projectName == null) {
            return Result.error(1005, "项目不存在");
        }

        // Generate version number
        String versionName = projectName + "-v" + (versionMapper.countByProjectId(projectId) + 1);

        // Save file
        File projectDir = new File(serverPath + "/versions/" + projectId + "/" + versionName);
        projectDir.mkdirs();
        File jarFile = new File(projectDir, originalFilename);
        try (FileOutputStream fos = new FileOutputStream(jarFile)) {
            fos.write(fileBytes);
        }

        String filePath = projectDir.getAbsolutePath();

        // Create version record
        VersionModel version = new VersionModel();
        version.setProjectId(projectId);
        version.setJarName(originalFilename);
        version.setFilePath(filePath);
        version.setFileSize(fileBytes.length);
        version.setVersion(versionName);
        version.setSha256(sha256Str);
        version.setRemark(remark);
        version.setCreateTime(System.currentTimeMillis());
        versionMapper.insert(version);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("version", versionName);
        data.put("filePath", filePath);
        return Result.success(data);
    }

    /**
     * DELETE /api/versions/{id} - 删除版本
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteVersion(@PathVariable Long id) {
        VersionModel version = versionMapper.findById(id);
        if (version == null) {
            return Result.error(1004, "版本不存在");
        }
        // Delete file from disk
        new File(version.getFilePath()).delete();
        versionMapper.deleteById(id);
        return Result.success();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
