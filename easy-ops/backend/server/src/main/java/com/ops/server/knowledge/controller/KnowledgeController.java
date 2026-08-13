package com.ops.server.knowledge.controller;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbCategoryModel;
import com.ops.common.model.KbCommentModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbImageModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbFavoriteService;
import com.ops.server.knowledge.service.KbRecentAccessService;
import com.ops.server.knowledge.service.KnowledgeBulkService;
import com.ops.server.knowledge.service.KnowledgeCategoryService;
import com.ops.server.knowledge.service.KnowledgeCommentService;
import com.ops.server.knowledge.service.KnowledgeDocumentService;
import com.ops.server.knowledge.service.KnowledgeImageService;
import com.ops.server.mapper.KbCategoryMapper;
import com.ops.server.service.TenantResourceAccessService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识库 REST 接口
 */
@RestController
@RequestMapping("/kb")
public class KnowledgeController {

    @Autowired
    private KnowledgeCategoryService categoryService;
    @Autowired
    private KnowledgeDocumentService documentService;
    @Autowired
    private KnowledgeBulkService bulkService;
    @Autowired
    private KnowledgeCommentService commentService;
    @Autowired
    private KnowledgeImageService imageService;
    @Autowired
    private KbFavoriteService favoriteService;
    @Autowired
    private KbRecentAccessService recentAccessService;
    @Autowired
    private SecurityContext securityContext;
    @Autowired
    private TenantResourceAccessService tenantResourceAccessService;
    @Autowired
    private KbCategoryMapper categoryMapper;

    @GetMapping("/categories")
    public Result<?> listCategories(@RequestParam(required = false) Long projectId) {
        if (projectId != null) {
            tenantResourceAccessService.requireProject(projectId);
        }
        return Result.success(categoryService.getCategoryTree(projectId));
    }

    @PostMapping("/categories")
    public Result<?> createCategory(@RequestBody KbCategoryModel category) {
        if (category.getProjectId() != null) {
            tenantResourceAccessService.requireProject(category.getProjectId());
        }
        // 分类本身共享；物化创建者租户便于归属审计
        if (category.getTenantId() == null) {
            category.setTenantId(securityContext.getCurrentTenantId());
        }
        return Result.success(categoryService.create(category));
    }

    @PutMapping("/categories/{id}")
    public Result<?> updateCategory(@PathVariable Long id, @RequestBody KbCategoryModel category) {
        KbCategoryModel existing = categoryMapper.findById(id);
        if (existing == null) {
            return Result.error(1004, "分类不存在");
        }
        requireCategoryAccess(existing);
        category.setId(id);
        category.setTenantId(existing.getTenantId());
        return Result.success(categoryService.update(category));
    }

    @DeleteMapping("/categories/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        try {
            KbCategoryModel existing = categoryMapper.findById(id);
            if (existing == null) {
                return Result.error(1004, "分类不存在");
            }
            requireCategoryAccess(existing);
            categoryService.delete(id);
            return Result.success();
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @GetMapping("/documents")
    public Result<?> listDocuments(@RequestParam Long categoryId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        requireCategoryAccess(categoryMapper.findById(categoryId));
        return Result.success(documentService.listByCategory(categoryId, securityContext.getCurrentTenantId(), page, pageSize));
    }

    @PostMapping("/documents")
    public Result<?> createDocument(@RequestBody KbDocumentModel document) {
        if (document.getProjectId() != null) {
            tenantResourceAccessService.requireProject(document.getProjectId());
        }
        if (document.getCategoryId() != null) {
            requireCategoryAccess(categoryMapper.findById(document.getCategoryId()));
        }
        document.setTenantId(securityContext.getCurrentTenantId());
        return Result.success(documentService.create(document));
    }

    @GetMapping("/documents/{id}")
    public Result<?> getDocument(@PathVariable Long id) {
        KbDocumentModel doc = tenantResourceAccessService.requireDocument(id);
        if (doc == null) {
            return Result.error(1004, "文档不存在");
        }
        documentService.incrementView(id, resolveTenantId(doc));
        // 记录最近访问
        Long userId = securityContext.getCurrentUserId();
        if (userId != null) {
            recentAccessService.recordAccess(id, userId, "VIEW");
        }
        return Result.success(doc);
    }

    @PutMapping("/documents/{id}")
    public Result<?> updateDocument(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            KbDocumentModel existing = tenantResourceAccessService.requireDocument(id);
            KbDocumentModel doc = mapDocument(body);
            doc.setTenantId(resolveTenantId(existing));
            Integer expectedVersion = body.get("versionNo") != null
                    ? Integer.parseInt(body.get("versionNo").toString()) : null;
            String changeNote = body.get("changeNote") != null ? body.get("changeNote").toString() : null;
            return Result.success(documentService.update(id, doc, expectedVersion, changeNote));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @DeleteMapping("/documents/{id}")
    public Result<?> deleteDocument(@PathVariable Long id) {
        KbDocumentModel doc = tenantResourceAccessService.requireDocument(id);
        documentService.delete(id, resolveTenantId(doc));
        return Result.success();
    }

    @PutMapping("/documents/{id}/move")
    public Result<?> moveDocument(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        KbDocumentModel doc = tenantResourceAccessService.requireDocument(id);
        Long targetCategoryId = body.get("categoryId");
        if (targetCategoryId != null) {
            requireCategoryAccess(categoryMapper.findById(targetCategoryId));
        }
        documentService.move(id, targetCategoryId, resolveTenantId(doc));
        return Result.success();
    }

    @PostMapping("/documents/{id}/lock")
    public Result<?> lockDocument(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        Map<String, Object> result = documentService.acquireLock(id);
        if (Boolean.TRUE.equals(result.get("conflict"))) {
            result.remove("conflict");
            Result<Map<String, Object>> r = Result.error(ErrorCode.KB_LOCK_CONFLICT, "文档正在被编辑");
            r.setData(result);
            return r;
        }
        return Result.success(result);
    }

    @PostMapping("/documents/{id}/unlock")
    public Result<?> unlockDocument(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        documentService.releaseLock(id);
        return Result.success();
    }

    @GetMapping("/documents/{id}/versions")
    public Result<?> listVersions(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        return Result.success(documentService.listVersions(id));
    }

    @GetMapping("/documents/{id}/versions/{ver}")
    public Result<?> getVersion(@PathVariable Long id, @PathVariable Integer ver) {
        tenantResourceAccessService.requireDocument(id);
        return Result.success(documentService.getVersion(id, ver));
    }

    @GetMapping("/documents/{id}/comments")
    public Result<?> listComments(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        return Result.success(commentService.listByDocument(id));
    }

    @PostMapping("/documents/{id}/comments")
    public Result<?> addComment(@PathVariable Long id, @RequestBody KbCommentModel comment) {
        tenantResourceAccessService.requireDocument(id);
        comment.setDocumentId(id);
        return Result.success(commentService.add(comment));
    }

    @PostMapping("/documents/{id}/images")
    public Result<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            tenantResourceAccessService.requireDocument(id);
            return Result.success(imageService.upload(id, file));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (IOException e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable Long imageId) {
        KbImageModel meta = imageService.findById(imageId);
        if (meta == null || meta.getDocumentId() == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            tenantResourceAccessService.requireDocument(meta.getDocumentId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).body(null);
        }
        File file = imageService.getImageFile(imageId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.IMAGE_PNG;
        if (meta.getMimeType() != null) {
            try {
                mediaType = MediaType.parseMediaType(meta.getMimeType());
            } catch (Exception ignored) {
                // use default
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/documents/{id}/export")
    public ResponseEntity<StreamingResponseBody> exportDocument(
            @PathVariable Long id, @RequestParam(defaultValue = "md") String format) throws IOException {
        KbDocumentModel doc = tenantResourceAccessService.requireDocument(id);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }
        String fileName = doc.getTitle().replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
        if ("zip".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(documentService.exportZip(id));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".md\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(documentService.exportMd(id));
    }

    /**
     * GET /api/kb/export - 全量导出所有文档（ZIP）
     */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> exportAll(
            @RequestParam(required = false) Long projectId) throws IOException {
        if (projectId != null) {
            tenantResourceAccessService.requireProject(projectId);
        }
        String fileName = "kb-export-" + System.currentTimeMillis() + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bulkService.exportAll(projectId, securityContext.getCurrentTenantId()));
    }

    /**
     * POST /api/kb/import - 全量导入文档（同分类同标题覆盖，否则新增）
     */
    @PostMapping("/import")
    public Result<?> importAll(@RequestParam("file") MultipartFile file,
                               @RequestParam(required = false) Long projectId) {
        try {
            if (projectId != null) {
                tenantResourceAccessService.requireProject(projectId);
            }
            return Result.success(bulkService.importAll(file, projectId, securityContext.getCurrentTenantId()));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (IOException e) {
            return Result.error(500, "导入失败: " + e.getMessage());
        }
    }

    // ====== 新增：收藏接口 ======

    /** 检查是否已收藏 */
    @GetMapping("/documents/{id}/favorite")
    public Result<?> checkFavorite(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        Long userId = securityContext.getCurrentUserId();
        boolean isFavorite = favoriteService.isFavorite(id, userId);
        return Result.success(isFavorite);
    }

    /** 收藏文档 */
    @PostMapping("/documents/{id}/favorite")
    public Result<?> addFavorite(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        Long userId = securityContext.getCurrentUserId();
        return Result.success(favoriteService.addFavorite(id, userId));
    }

    /** 取消收藏 */
    @DeleteMapping("/documents/{id}/favorite")
    public Result<?> removeFavorite(@PathVariable Long id) {
        tenantResourceAccessService.requireDocument(id);
        Long userId = securityContext.getCurrentUserId();
        favoriteService.removeFavorite(id, userId);
        return Result.success();
    }

    // ====== 新增：收藏列表接口 ======

    /** 收藏列表（当前用户所有收藏） */
    @GetMapping("/favorites")
    public Result<?> listFavorites() {
        Long userId = securityContext.getCurrentUserId();
        return Result.success(favoriteService.listByUser(userId));
    }

    /** 添加收藏（按 documentId） */
    @PostMapping("/favorites")
    public Result<?> addFavoriteByDocId(@RequestBody Map<String, Object> body) {
        Long documentId = Long.parseLong(body.get("documentId").toString());
        tenantResourceAccessService.requireDocument(documentId);
        Long userId = securityContext.getCurrentUserId();
        return Result.success(favoriteService.addFavorite(documentId, userId));
    }

    /** 移除收藏（按 documentId） */
    @DeleteMapping("/favorites/{documentId}")
    public Result<?> removeFavoriteByDocId(@PathVariable Long documentId) {
        tenantResourceAccessService.requireDocument(documentId);
        Long userId = securityContext.getCurrentUserId();
        favoriteService.removeFavorite(documentId, userId);
        return Result.success();
    }

    // ====== 新增：最近访问接口 ======

    /** 最近访问列表 */
    @GetMapping("/recent")
    public Result<?> listRecent(@RequestParam(defaultValue = "20") Integer limit) {
        Long userId = securityContext.getCurrentUserId();
        return Result.success(recentAccessService.listByUser(userId, limit));
    }

    private KbDocumentModel mapDocument(Map<String, Object> body) {
        KbDocumentModel doc = new KbDocumentModel();
        if (body.get("categoryId") != null) {
            doc.setCategoryId(Long.parseLong(body.get("categoryId").toString()));
        }
        if (body.get("title") != null) {
            doc.setTitle(body.get("title").toString());
        }
        if (body.get("content") != null) {
            doc.setContent(body.get("content").toString());
        }
        if (body.get("status") != null) {
            doc.setStatus(Integer.parseInt(body.get("status").toString()));
        }
        if (body.get("projectId") != null) {
            doc.setProjectId(Long.parseLong(body.get("projectId").toString()));
        }
        return doc;
    }

    /**
     * 校验分类归属：分类可按 projectId 隔离，否则校验分类所属租户。
     * 分类本身共享（tenant_id=0 的全局分类）对所有租户可见。
     */
    private void requireCategoryAccess(KbCategoryModel category) {
        if (category == null) {
            throw new IllegalArgumentException("分类不存在");
        }
        Long tenantId = securityContext.getCurrentTenantId();
        if (tenantId != null && !securityContext.isPlatformAdmin()) {
            if (category.getProjectId() != null) {
                tenantResourceAccessService.requireProject(category.getProjectId());
            } else if (category.getTenantId() != null && category.getTenantId() > 0
                    && !tenantId.equals(category.getTenantId())) {
                throw new IllegalArgumentException("无权访问该分类");
            }
        }
    }

    /** 文档 tenant_id 可能为 null（历史数据），统一归 0 以便 tenant 过滤命中 */
    private Long resolveTenantId(KbDocumentModel doc) {
        return doc != null && doc.getTenantId() != null ? doc.getTenantId() : 0L;
    }
}
