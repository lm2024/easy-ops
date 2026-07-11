package com.ops.server.knowledge.service;

import com.ops.common.constant.ErrorCode;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbDocumentLockModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.common.model.KbDocumentVersionModel;
import com.ops.server.mapper.KbCommentMapper;
import com.ops.server.mapper.KbDocumentLockMapper;
import com.ops.server.mapper.KbDocumentMapper;
import com.ops.server.mapper.KbDocumentVersionMapper;
import com.ops.server.mapper.KbImageMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 知识库文档服务：CRUD、锁、版本、导出、Yjs 状态存取
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeDocumentService {

    private static final long LOCK_TTL_MS = 30L * 60 * 1000;
    private static final int MAX_VERSIONS = 50;

    @Autowired
    private KbDocumentMapper documentMapper;
    @Autowired
    private KbDocumentVersionMapper versionMapper;
    @Autowired
    private KbDocumentLockMapper lockMapper;
    @Autowired
    private KbCommentMapper commentMapper;
    @Autowired
    private KbImageMapper imageMapper;
    @Autowired
    private SecurityContext securityContext;

    @Value("${server.path:./data}")
    private String dataPath;

    public KbDocumentModel findById(Long id) {
        return documentMapper.findById(id);
    }

    public KbDocumentModel findByCategoryAndTitle(Long categoryId, String title) {
        return documentMapper.findByCategoryAndTitle(categoryId, title);
    }

    /**
     * 全量导入时覆盖已有文档（跳过编辑锁校验）。
     */
    public KbDocumentModel importOverwrite(Long id, String title, String content, Integer status) {
        KbDocumentModel existing = documentMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        Long userId = securityContext.getCurrentUserId();
        existing.setTitle(title != null ? title : existing.getTitle());
        existing.setContent(content != null ? content : "");
        existing.setContentSize(existing.getContent().length());
        existing.setSummary(buildSummary(existing.getContent()));
        existing.setStatus(status != null ? status : 1);
        existing.setLastEditorId(userId);
        existing.setVersionNo(existing.getVersionNo() != null ? existing.getVersionNo() + 1 : 1);
        existing.setUpdateTime(System.currentTimeMillis());
        documentMapper.update(existing);
        saveVersion(existing, userId, "全量导入覆盖");
        versionMapper.deleteOldestBeyond(id, MAX_VERSIONS);
        return documentMapper.findById(id);
    }

    public Map<String, Object> listByCategory(Long categoryId, Integer page, Integer pageSize) {
        List<KbDocumentModel> list = documentMapper.findByCategory(categoryId, page, pageSize);
        Long total = documentMapper.countByCategory(categoryId);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public KbDocumentModel create(KbDocumentModel doc) {
        long now = System.currentTimeMillis();
        Long userId = securityContext.getCurrentUserId();
        if (doc.getStatus() == null) {
            doc.setStatus(1);
        }
        if (doc.getVersionNo() == null) {
            doc.setVersionNo(1);
        }
        if (doc.getViewCount() == null) {
            doc.setViewCount(0);
        }
        doc.setAuthorId(userId);
        doc.setLastEditorId(userId);
        doc.setContentSize(doc.getContent() != null ? doc.getContent().length() : 0);
        doc.setSummary(buildSummary(doc.getContent()));
        doc.setCreateTime(now);
        doc.setUpdateTime(now);
        documentMapper.insert(doc);
        if (doc.getStatus() == 1) {
            saveVersion(doc, userId, "初始发布");
        }
        return doc;
    }

    public KbDocumentModel update(Long id, KbDocumentModel doc, Integer expectedVersion, String changeNote) {
        KbDocumentModel existing = documentMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        if (expectedVersion != null && !expectedVersion.equals(existing.getVersionNo())) {
            throw new BusinessException(ErrorCode.KB_VERSION_CONFLICT, "版本冲突，请刷新后重试");
        }
        // CRDT 模式下如果 yjsState 存在则跳过强制锁校验
        if (existing.getYjsState() == null || existing.getYjsState().length == 0) {
            assertLockHolder(id);
        }
        Long userId = securityContext.getCurrentUserId();
        doc.setId(id);
        if (doc.getCategoryId() == null) {
            doc.setCategoryId(existing.getCategoryId());
        }
        if (doc.getTitle() == null) {
            doc.setTitle(existing.getTitle());
        }
        if (doc.getContent() == null) {
            doc.setContent(existing.getContent());
        }
        if (doc.getStatus() == null) {
            doc.setStatus(existing.getStatus());
        }
        doc.setAuthorId(existing.getAuthorId());
        doc.setViewCount(existing.getViewCount());
        doc.setCreateTime(existing.getCreateTime());
        doc.setSourceType(existing.getSourceType());
        doc.setSourceId(existing.getSourceId());
        doc.setProjectId(existing.getProjectId());
        doc.setLastEditorId(userId);
        doc.setContentSize(doc.getContent() != null ? doc.getContent().length() : 0);
        doc.setSummary(buildSummary(doc.getContent()));
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            doc.setVersionNo(existing.getVersionNo() + 1);
        } else {
            doc.setVersionNo(existing.getVersionNo());
        }
        doc.setUpdateTime(System.currentTimeMillis());
        documentMapper.update(doc);
        if (doc.getStatus() != null && doc.getStatus() == 1) {
            KbDocumentModel updated = documentMapper.findById(id);
            String note = changeNote;
            saveVersion(updated, userId, note);
            versionMapper.deleteOldestBeyond(id, MAX_VERSIONS);
        }
        return documentMapper.findById(id);
    }

    public void delete(Long id) {
        documentMapper.deleteById(id);
        versionMapper.deleteByDocumentId(id);
        commentMapper.deleteByDocumentId(id);
        imageMapper.deleteByDocumentId(id);
        lockMapper.deleteByDocumentId(id);
    }

    public void move(Long id, Long categoryId) {
        documentMapper.updateCategory(id, categoryId, System.currentTimeMillis());
    }

    public Map<String, Object> acquireLock(Long documentId) {
        lockMapper.deleteExpired(System.currentTimeMillis());
        Long userId = securityContext.getCurrentUserId();
        String userName = securityContext.getCurrentUsername();
        if (userName == null) {
            userName = "user";
        }
        KbDocumentLockModel existing = lockMapper.findByDocumentId(documentId);
        long now = System.currentTimeMillis();
        if (existing != null && existing.getExpireTime() > now && !existing.getUserId().equals(userId)) {
            Map<String, Object> conflict = new HashMap<String, Object>();
            conflict.put("locked", false);
            Map<String, Object> holder = new HashMap<String, Object>();
            holder.put("userId", existing.getUserId());
            holder.put("userName", existing.getUserName());
            conflict.put("holder", holder);
            conflict.put("expireTime", existing.getExpireTime());
            conflict.put("conflict", true);
            return conflict;
        }
        KbDocumentLockModel lock = new KbDocumentLockModel();
        lock.setDocumentId(documentId);
        lock.setUserId(userId);
        lock.setUserName(userName);
        lock.setLockTime(now);
        lock.setExpireTime(now + LOCK_TTL_MS);
        lockMapper.upsert(lock);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("locked", true);
        result.put("expireTime", lock.getExpireTime());
        Map<String, Object> holder = new HashMap<String, Object>();
        holder.put("userId", userId);
        holder.put("userName", userName);
        result.put("holder", holder);
        return result;
    }

    public void releaseLock(Long documentId) {
        KbDocumentLockModel lock = lockMapper.findByDocumentId(documentId);
        Long userId = securityContext.getCurrentUserId();
        if (lock != null && lock.getUserId().equals(userId)) {
            lockMapper.deleteByDocumentId(documentId);
        }
    }

    public List<KbDocumentVersionModel> listVersions(Long documentId) {
        return versionMapper.findByDocumentId(documentId);
    }

    public KbDocumentVersionModel getVersion(Long documentId, Integer versionNo) {
        return versionMapper.findByDocAndVersion(documentId, versionNo);
    }

    public Map<String, Object> search(String keyword, Integer page, Integer pageSize) {
        List<KbDocumentModel> list = documentMapper.search(keyword, page, pageSize);
        Long total = documentMapper.countSearch(keyword);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", list);
        result.put("total", total);
        return result;
    }

    public void incrementView(Long id) {
        documentMapper.incrementViewCount(id);
    }

    public StreamingResponseBody exportMd(Long id) {
        KbDocumentModel doc = documentMapper.findById(id);
        if (doc == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        String content = doc.getContent();
        return outputStream -> {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        };
    }

    public StreamingResponseBody exportZip(Long id) throws IOException {
        KbDocumentModel doc = documentMapper.findById(id);
        if (doc == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        return outputStream -> {
            ZipOutputStream zos = new ZipOutputStream(outputStream);
            zos.putNextEntry(new ZipEntry("document.md"));
            zos.write(doc.getContent().getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.finish();
            zos.flush();
        };
    }

    // ====== 新增：Yjs 状态存取 ======

    /**
     * 保存 Yjs 状态到 kb_document.yjs_state
     */
    public void saveYjsState(Long id, byte[] yjsState) {
        documentMapper.updateYjsState(id, yjsState);
    }

    /**
     * 加载 Yjs 状态
     */
    public byte[] loadYjsState(Long id) {
        return documentMapper.selectYjsState(id);
    }

    /**
     * 从 Yjs 导出的 Markdown 更新文档 content
     */
    public KbDocumentModel updateContentFromYjs(Long id, String content) {
        KbDocumentModel existing = documentMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        int contentSize = content != null ? content.length() : 0;
        documentMapper.updateContentFromYjs(id, content, contentSize, System.currentTimeMillis());
        return documentMapper.findById(id);
    }

    // ====== 内部方法 ======

    private void saveVersion(KbDocumentModel doc, Long editorId, String changeNote) {
        KbDocumentVersionModel ver = new KbDocumentVersionModel();
        ver.setDocumentId(doc.getId());
        ver.setVersionNo(doc.getVersionNo());
        ver.setTitle(doc.getTitle());
        ver.setContent(doc.getContent());
        ver.setEditorId(editorId);
        ver.setChangeNote(changeNote != null ? changeNote : "");
        ver.setCreateTime(System.currentTimeMillis());
        versionMapper.insert(ver);
    }

    private void assertLockHolder(Long documentId) {
        KbDocumentLockModel lock = lockMapper.findByDocumentId(documentId);
        Long userId = securityContext.getCurrentUserId();
        if (lock != null && lock.getExpireTime() > System.currentTimeMillis()
                && !lock.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.KB_LOCK_CONFLICT, "文档正在被 " + lock.getUserName() + " 编辑");
        }
    }

    private String buildSummary(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("[#*`>\\[\\]]", "").trim();
        return plain.length() > 200 ? plain.substring(0, 200) : plain;
    }

    // ====== Yjs 协作相关方法 ======

    /**
     * 获取文档内容（用于 Yjs 协作同步）
     */
    public String getDocumentContent(Long docId) {
        KbDocumentModel doc = documentMapper.findById(docId);
        return doc != null ? doc.getContent() : "";
    }

    /**
     * 保存文档内容（用于 Yjs 协作同步）
     */
    public void saveDocumentContent(Long docId, String content) {
        KbDocumentModel doc = documentMapper.findById(docId);
        if (doc == null) {
            throw new BusinessException(1004, "文档不存在");
        }
        
        long now = System.currentTimeMillis();
        doc.setContent(content);
        doc.setContentSize(content != null ? content.length() : 0);
        doc.setUpdateTime(now);
        
        // 创建新版本
        Long userId = securityContext.getCurrentUserId();
        saveVersion(doc, userId != null ? userId : 1L, "协作编辑自动保存");
        
        documentMapper.update(doc);
    }
}
