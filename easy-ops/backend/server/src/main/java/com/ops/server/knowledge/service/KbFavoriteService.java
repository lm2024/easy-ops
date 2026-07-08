package com.ops.server.knowledge.service;

import com.ops.common.model.KbFavoriteModel;
import com.ops.server.mapper.KbFavoriteMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收藏服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KbFavoriteService {

    @Autowired
    private KbFavoriteMapper favoriteMapper;

    /**
     * 用户收藏列表
     */
    public List<KbFavoriteModel> listByUser(Long userId) {
        return favoriteMapper.findByUserId(userId);
    }

    /**
     * 收藏文档
     */
    public KbFavoriteModel addFavorite(Long documentId, Long userId) {
        KbFavoriteModel existing = favoriteMapper.findByDocumentIdAndUserId(documentId, userId);
        if (existing != null) {
            return existing; // 已收藏，不重复添加
        }
        KbFavoriteModel favorite = new KbFavoriteModel();
        favorite.setDocumentId(documentId);
        favorite.setUserId(userId);
        favorite.setCreateTime(System.currentTimeMillis());
        favoriteMapper.insert(favorite);
        return favorite;
    }

    /**
     * 取消收藏
     */
    public void removeFavorite(Long documentId, Long userId) {
        favoriteMapper.deleteByDocumentIdAndUserId(documentId, userId);
    }

    /**
     * 是否已收藏
     */
    public boolean isFavorite(Long documentId, Long userId) {
        KbFavoriteModel existing = favoriteMapper.findByDocumentIdAndUserId(documentId, userId);
        return existing != null;
    }
}
