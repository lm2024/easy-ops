package com.ops.server.mapper;

import com.ops.common.model.ScriptDistributeRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 脚本分发记录 Mapper
 */
@Mapper
public interface ScriptDistributeRecordMapper {

    ScriptDistributeRecordModel findById(@Param("id") Long id);

    List<ScriptDistributeRecordModel> findByProjectId(@Param("projectId") Long projectId);

    List<ScriptDistributeRecordModel> findByScriptFileId(@Param("scriptFileId") Long scriptFileId);

    int insert(ScriptDistributeRecordModel model);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("resultDetail") String resultDetail, @Param("tenantId") Long tenantId);

    int deleteById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** 定时清理：删除 create_time 早于 cutoff 的历史分发记录 */
    int deleteBefore(@Param("cutoff") long cutoff);
}
