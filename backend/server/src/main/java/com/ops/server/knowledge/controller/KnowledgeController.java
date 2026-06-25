package com.ops.server.knowledge.controller;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbCategoryModel;
import com.ops.common.model.KbCommentModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbImageModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KnowledgeCategoryService;
import com.ops.server.knowledge.service.KnowledgeCommentService;
import com.ops.server.knowledge.service.KnowledgeDocumentService;
import com.ops.server.knowledge.service.KnowledgeImageService;
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
    private KnowledgeCommentService commentService;
    @Autowired
    private KnowledgeImageService imageService;
    @Autowired
    private SecurityContext securityContext;

    @GetMapping("/categories")
    public Result<?> listCategories(@RequestParam(required = false) Long projectId) {
        return Result.success(categoryService.getCategoryTree(projectId));
    }

    @PostMapping("/categories")
    public Result<?> createCategory(@RequestBody KbCategoryModel category) {
        return Result.success(categoryService.create(category));
    }

    @PutMapping("/categories/{id}")
    public Result<?> updateCategory(@PathVariable Long id, @RequestBody KbCategoryModel category) {
        category.setId(id);
        return Result.success(categoryService.update(category));
    }

    @DeleteMapping("/categories/{id}")
    public Result<?> deleteCategory(@PathVariable Long id) {
        try {
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
        return Result.success(documentService.listByCategory(categoryId, page, pageSize));
    }

    @PostMapping("/documents")
    public Result<?> createDocument(@RequestBody KbDocumentModel document) {
        return Result.success(documentService.create(document));
    }

    @GetMapping("/documents/{id}")
    public Result<?> getDocument(@PathVariable Long id) {
        KbDocumentModel doc = documentService.findById(id);
        if (doc == null) {
            return Result.error(1004, "文档不存在");
        }
        documentService.incrementView(id);
        return Result.success(doc);
    }

    @PutMapping("/documents/{id}")
    public Result<?> updateDocument(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            KbDocumentModel doc = mapDocument(body);
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
        documentService.delete(id);
        return Result.success();
    }

    @PutMapping("/documents/{id}/move")
    public Result<?> moveDocument(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        documentService.move(id, body.get("categoryId"));
        return Result.success();
    }

    @PostMapping("/documents/{id}/lock")
    public Result<?> lockDocument(@PathVariable Long id) {
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
        documentService.releaseLock(id);
        return Result.success();
    }

    @GetMapping("/documents/{id}/versions")
    public Result<?> listVersions(@PathVariable Long id) {
        return Result.success(documentService.listVersions(id));
    }

    @GetMapping("/documents/{id}/versions/{ver}")
    public Result<?> getVersion(@PathVariable Long id, @PathVariable Integer ver) {
        return Result.success(documentService.getVersion(id, ver));
    }

    @GetMapping("/documents/{id}/comments")
    public Result<?> listComments(@PathVariable Long id) {
        return Result.success(commentService.listByDocument(id));
    }

    @PostMapping("/documents/{id}/comments")
    public Result<?> addComment(@PathVariable Long id, @RequestBody KbCommentModel comment) {
        comment.setDocumentId(id);
        return Result.success(commentService.add(comment));
    }

    @PostMapping("/documents/{id}/images")
    public Result<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            return Result.success(imageService.upload(id, file));
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (IOException e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/images/{imageId}")
    public ResponseEntity<Resource> getImage(@PathVariable Long imageId) {
        File file = imageService.getImageFile(imageId);
        if (file == null || !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        KbImageModel meta = imageService.findById(imageId);
        MediaType mediaType = MediaType.IMAGE_PNG;
        if (meta != null && meta.getMimeType() != null) {
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
        KbDocumentModel doc = documentService.findById(id);
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

    @GetMapping("/search")
    public Result<?> search(@RequestParam String q,
                            @RequestParam(defaultValue = "1") Integer page,
                            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(documentService.search(q, page, pageSize));
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
}
