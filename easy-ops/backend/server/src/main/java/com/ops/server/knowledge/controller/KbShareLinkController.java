package com.ops.server.knowledge.controller;

import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbShareLinkModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KbShareLinkService;
import com.ops.server.knowledge.service.KnowledgeDocumentService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分享 REST 接口
 */
@RestController
@RequestMapping("/kb/share-links")
public class KbShareLinkController {

    @Autowired
    private KbShareLinkService shareLinkService;

    @Autowired
    private KnowledgeDocumentService documentService;

    @Autowired
    private SecurityContext securityContext;

    /** 创建分享链接 */
    @PostMapping
    public Result<?> createShareLink(@RequestBody Map<String, Object> body) {
        Long documentId = Long.parseLong(body.get("documentId").toString());
        String password = body.get("password") != null ? body.get("password").toString() : "";
        Long expireTime = body.get("expireTime") != null ? Long.parseLong(body.get("expireTime").toString()) : 0L;
        Long createUserId = securityContext.getCurrentUserId();
        KbShareLinkModel link = shareLinkService.createShareLink(documentId, password, expireTime, createUserId);
        return Result.success(link);
    }

    /** 获取分享链接 */
    @GetMapping("/{id}")
    public Result<?> getShareLink(@PathVariable Long id) {
        return Result.success(shareLinkService.getByDocumentId(id));
    }

    /** 删除分享链接 */
    @DeleteMapping("/{id}")
    public Result<?> deleteShareLink(@PathVariable Long id) {
        shareLinkService.deleteShareLink(id);
        return Result.success();
    }

    /** 通过 token 访问分享链接 */
    @GetMapping("/access/{token}")
    public Result<?> accessByToken(@PathVariable String token,
                                   @RequestParam(required = false) String password) {
        try {
            KbShareLinkModel link = shareLinkService.checkAccess(token, password);
            // 验证通过后返回文档内容
            KbDocumentModel doc = documentService.findById(link.getDocumentId());
            if (doc == null) {
                return Result.error(1004, "文档不存在");
            }
            return Result.success(doc);
        } catch (Exception e) {
            return Result.error(e.getMessage().contains("不存在") ? 1004 : 403, e.getMessage());
        }
    }
}
