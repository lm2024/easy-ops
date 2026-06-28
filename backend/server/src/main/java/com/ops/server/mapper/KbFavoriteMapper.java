package com.ops.server.mapper;

import com.ops.common.model.KbFavoriteModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbFavoriteMapper {
    List<KbFavoriteModel> selectAll();
    KbFavoriteModel selectById(@Param("id") Long id);
    int insert(KbFavoriteModel favorite);
    int update(KbFavoriteModel favorite);
    int delete(@Param("id") Long id);

    /** 查询用户的收藏列表 */
    List<KbFavoriteModel> findByUserId(@Param("userId") Long userId);

    /** 查询特定收藏记录 */
    KbFavoriteModel findByDocumentIdAndUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);

    /** 取消收藏 */
    int deleteByDocumentIdAndUserId(@Param("documentId") Long documentId, @Param("userId") Long userId);
}
