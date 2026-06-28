package com.ops.server.mapper;

import com.ops.common.model.KbDocumentPermissionModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentPermissionMapper {
    List<KbDocumentPermissionModel> selectAll();
    KbDocumentPermissionModel selectById(@Param("id") Long id);
    int insert(KbDocumentPermissionModel permission);
    int update(KbDocumentPermissionModel permission);
    int delete(@Param("id") Long id);

    /** 查询特定目标和用户的权限记录 */
    KbDocumentPermissionModel findByTargetAndUser(@Param("targetId") Long targetId,
                                                   @Param("targetType") String targetType,
                                                   @Param("userId") Long userId);

    /** 查询目标的所有权限列表 */
    List<KbDocumentPermissionModel> findByTarget(@Param("targetId") Long targetId,
                                                  @Param("targetType") String targetType);

    /** 删除特定目标和用户的权限 */
    int deleteByTargetAndUser(@Param("targetId") Long targetId,
                              @Param("targetType") String targetType,
                              @Param("userId") Long userId);
}
