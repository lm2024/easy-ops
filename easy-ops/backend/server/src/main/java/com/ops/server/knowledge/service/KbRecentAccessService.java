package com.ops.server.knowledge.service;

import com.ops.common.model.KbRecentAccessModel;
import com.ops.server.mapper.KbRecentAccessMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 最近访问服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbRecentAccessService {

    @Autowired
    private KbRecentAccessMapper recentAccessMapper;

    /**
     * 用户最近访问列表
     */
    public List<KbRecentAccessModel> listByUser(Long userId, Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 20;
        }
        return recentAccessMapper.findByUserId(userId, limit);
    }

    /**
     * 记录访问（先删旧记录再插入，确保不重复）
     */
    public KbRecentAccessModel recordAccess(Long documentId, Long userId, String accessType) {
        // 先删除已有记录，确保同一用户同一文档只有一条记录
        recentAccessMapper.deleteByUserIdAndDocumentId(userId, documentId);
        KbRecentAccessModel access = new KbRecentAccessModel();
        access.setDocumentId(documentId);
        access.setUserId(userId);
        if (accessType == null || accessType.isEmpty()) {
            accessType = "VIEW";
        }
        access.setAccessType(accessType);
        access.setCreateTime(System.currentTimeMillis());
        recentAccessMapper.insert(access);
        return access;
    }

    /**
     * 删除特定记录
     */
    public void deleteByUserAndDocument(Long userId, Long documentId) {
        recentAccessMapper.deleteByUserIdAndDocumentId(userId, documentId);
    }
}
