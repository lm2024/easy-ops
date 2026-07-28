package com.ops.server.mapper;

import com.ops.common.model.GlobalScriptDistributeRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 全局脚本分发记录 Mapper
 */
@Mapper
public interface GlobalScriptDistributeRecordMapper {

    GlobalScriptDistributeRecordModel findById(@Param("id") Long id);

    List<GlobalScriptDistributeRecordModel> findAll();

    List<GlobalScriptDistributeRecordModel> findByScriptFileId(@Param("scriptFileId") Long scriptFileId);

    int insert(GlobalScriptDistributeRecordModel model);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("resultDetail") String resultDetail);

    int deleteById(@Param("id") Long id);
}
