package com.ops.server.controller;

import com.ops.common.model.VersionModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.VersionPackageMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.service.AuditLogService;
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
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private AuditLogService auditLog;

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
        List<VersionModel> versions = versionPackageMapper.findByProjectId(projectId, page, pageSize);
        Long total = versionPackageMapper.countByProjectId(projectId);
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
        VersionModel version = versionPackageMapper.findById(id);
        return version != null ? Result.success(version) : Result.error(1004, "版本不存在");
    }

    /**
     * POST /api/versions/upload - 上传Jar包
     */
    @PostMapping("/upload")
    public Result<?> uploadVersion(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long projectId,
            @RequestParam(required = false, defaultValue = "jar") String packageType,
            @RequestParam(required = false) String remark) throws Exception {
        if (file == null || file.isEmpty()) {
            return Result.paramError("请选择文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            return Result.paramError("文件名无效");
        }

        ProjectModel project = projectMapper.findById(projectId);
        if (project == null) {
            return Result.error(1005, "项目不存在");
        }

        boolean isFrontend = "frontend".equalsIgnoreCase(packageType)
                || originalFilename.toLowerCase().endsWith(".zip");
        if (isFrontend) {
            if (!originalFilename.toLowerCase().endsWith(".zip")) {
                return Result.error(1006, "前端包仅支持 .zip 文件（如 dist.zip）");
            }
        } else {
            if (!originalFilename.toLowerCase().endsWith(".jar")) {
                return Result.error(1006, "仅支持 .jar 文件");
            }
            if (project.getJarName() != null && !project.getJarName().trim().isEmpty()
                    && !originalFilename.equals(project.getJarName())) {
                return Result.paramError("Jar 包名必须为 " + project.getJarName()
                        + "，当前上传: " + originalFilename);
            }
        }

        // Calculate SHA-256
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = file.getBytes();
        String sha256Str = bytesToHex(sha256.digest(fileBytes));

        // Get project name
        String projectName = versionPackageMapper.getProjectNameByProjectId(projectId);
        if (projectName == null) {
            return Result.error(1005, "项目不存在");
        }

        // Generate version number
        String versionName = projectName + "-v" + (versionPackageMapper.countByProjectId(projectId) + 1);

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
        version.setFileSize((long) fileBytes.length);
        version.setVersion(versionName);
        version.setSha256(sha256Str);
        version.setRemark(remark);
        version.setPackageType(isFrontend ? "frontend" : "jar");
        version.setCreateTime(System.currentTimeMillis());
        versionPackageMapper.insert(version);

        Map<String, Object> data = new java.util.HashMap<>();
        data.put("version", versionName);
        data.put("filePath", filePath);
        auditLog.log("VERSION", "UPLOAD", "上传版本包: " + originalFilename + ", 项目ID=" + projectId);
        return Result.success(data);
    }

    /**
     * DELETE /api/versions/{id} - 删除版本
     */
    @DeleteMapping("/{id}")
    public Result<?> deleteVersion(@PathVariable Long id) {
        VersionModel version = versionPackageMapper.findById(id);
        if (version == null) {
            return Result.error(1004, "版本不存在");
        }
        // Delete file from disk
        new File(version.getFilePath()).delete();
        versionPackageMapper.deleteById(id);
        auditLog.log("VERSION", "DELETE", "删除版本包: " + version.getJarName() + " (ID=" + id + ")");
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
