package com.ops.server.knowledge.controller;

import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbDocumentVersionModel;
import com.ops.common.response.Result;
import com.ops.server.knowledge.service.KnowledgeDocumentService;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 协作 REST 接口
 * WebSocket 通道负责实时协作，REST 用于保存、回滚、Diff 查询等
 */
@RestController
@RequestMapping("/kb/collab")
public class KbCollabController {

    @Autowired
    private KnowledgeDocumentService documentService;

    @Autowired
    private SecurityContext securityContext;

    /** 保存协作状态（Yjs→Markdown） */
    @PostMapping("/{documentId}/save")
    public Result<?> saveCollabState(@PathVariable Long documentId, @RequestBody Map<String, Object> body) {
        String content = body.get("content") != null ? body.get("content").toString() : null;
        if (content != null) {
            documentService.updateContentFromYjs(documentId, content);
        }
        // 如果 body 中包含 yjsState（byte[] 以 base64 传递），则也保存 Yjs 状态
        // 注意：前端传 base64 字符串，后端暂不处理 byte[] 的 JSON 序列化
        // yjsState 的保存由 WebSocket 断连时服务端自动执行
        return Result.success();
    }

    /** 获取在线用户列表（由 WebSocket session 管理） */
    @GetMapping("/{documentId}/online")
    public Result<?> getOnlineUsers(@PathVariable Long documentId) {
        // WebSocket Handler 不暴露给 Controller，此处返回占位
        // 实际在线用户列表由前端通过 Yjs Awareness 协议获取
        return Result.success(new java.util.ArrayList<>());
    }

    /** 版本回滚 */
    @PostMapping("/{documentId}/rollback")
    public Result<?> rollbackVersion(@PathVariable Long documentId, @RequestBody Map<String, Object> body) {
        Integer versionNo = body.get("versionNo") != null ? Integer.parseInt(body.get("versionNo").toString()) : null;
        if (versionNo == null) {
            return Result.paramError("versionNo 不能为空");
        }
        KbDocumentVersionModel version = documentService.getVersion(documentId, versionNo);
        if (version == null) {
            return Result.error(1004, "版本不存在");
        }
        // 用版本内容回滚文档
        KbDocumentModel doc = new KbDocumentModel();
        doc.setContent(version.getContent());
        doc.setTitle(version.getTitle());
        doc.setStatus(1);
        try {
            KbDocumentModel updated = documentService.update(documentId, doc, versionNo, "版本回滚至 v" + versionNo);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }

    /** 获取版本 Diff */
    @GetMapping("/{documentId}/diff")
    public Result<?> getVersionDiff(@PathVariable Long documentId,
                                    @RequestParam Integer fromVersion,
                                    @RequestParam Integer toVersion) {
        KbDocumentVersionModel fromVer = documentService.getVersion(documentId, fromVersion);
        KbDocumentVersionModel toVer = documentService.getVersion(documentId, toVersion);
        if (fromVer == null || toVer == null) {
            return Result.error(1004, "版本不存在");
        }
        Map<String, Object> diff = new java.util.HashMap<String, Object>();
        diff.put("fromVersion", fromVer);
        diff.put("toVersion", toVer);
        diff.put("fromContent", fromVer.getContent());
        diff.put("toContent", toVer.getContent());
        return Result.success(diff);
    }
}
