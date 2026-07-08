package com.ops.server.mapper;

import com.ops.common.model.KbRecentAccessModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbRecentAccessMapper {
    List<KbRecentAccessModel> selectAll();
    KbRecentAccessModel selectById(@Param("id") Long id);
    int insert(KbRecentAccessModel recentAccess);
    int update(KbRecentAccessModel recentAccess);
    int delete(@Param("id") Long id);

    /** 查询用户最近访问（带 limit） */
    List<KbRecentAccessModel> findByUserId(@Param("userId") Long userId, @Param("limit") Integer limit);

    /** 删除特定用户-文档记录 */
    int deleteByUserIdAndDocumentId(@Param("userId") Long userId, @Param("documentId") Long documentId);
}
