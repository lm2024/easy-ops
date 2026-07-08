package com.ops.server.knowledge.service;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbShareLinkModel;
import com.ops.server.mapper.KbShareLinkMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 分享链接服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbShareLinkService {

    @Autowired
    private KbShareLinkMapper shareLinkMapper;

    /**
     * 创建分享链接（生成 UUID token）
     */
    public KbShareLinkModel createShareLink(Long documentId, String password, Long expireTime, Long createUserId) {
        KbShareLinkModel link = new KbShareLinkModel();
        link.setDocumentId(documentId);
        link.setToken(UUID.randomUUID().toString());
        link.setPassword(password != null ? password : "");
        link.setExpireTime(expireTime != null ? expireTime : 0L);
        link.setCreateUserId(createUserId);
        link.setCreateTime(System.currentTimeMillis());
        shareLinkMapper.insert(link);
        return link;
    }

    /**
     * 按 token 获取分享链接
     */
    public KbShareLinkModel getByToken(String token) {
        return shareLinkMapper.findByToken(token);
    }

    /**
     * 文档分享链接列表
     */
    public List<KbShareLinkModel> getByDocumentId(Long documentId) {
        return shareLinkMapper.findByDocumentId(documentId);
    }

    /**
     * 删除分享链接
     */
    public void deleteShareLink(Long id) {
        shareLinkMapper.delete(id);
    }

    /**
     * 验证分享访问（密码+过期时间校验）
     * @param token 分享 token
     * @param password 提供的密码（可为空）
     * @return 校验通过返回分享链接，否则抛异常
     */
    public KbShareLinkModel checkAccess(String token, String password) {
        KbShareLinkModel link = shareLinkMapper.findByToken(token);
        if (link == null) {
            throw new BusinessException(1004, "分享链接不存在");
        }
        // 过期时间校验
        if (link.getExpireTime() != null && link.getExpireTime() > 0) {
            if (System.currentTimeMillis() > link.getExpireTime()) {
                throw new BusinessException(403, "分享链接已过期");
            }
        }
        // 密码校验
        if (link.getPassword() != null && !link.getPassword().isEmpty()) {
            if (password == null || !link.getPassword().equals(password)) {
                throw new BusinessException(403, "分享密码错误");
            }
        }
        return link;
    }
}
