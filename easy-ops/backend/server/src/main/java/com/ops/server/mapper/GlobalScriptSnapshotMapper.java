package com.ops.server.mapper;

import com.ops.common.model.GlobalScriptSnapshotModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 全局脚本快照 Mapper
 */
@Mapper
public interface GlobalScriptSnapshotMapper {

    GlobalScriptSnapshotModel findById(@Param("id") Long id);

    GlobalScriptSnapshotModel findByNodeAndFile(@Param("nodeId") Long nodeId, @Param("scriptFileId") Long scriptFileId);

    List<GlobalScriptSnapshotModel> findByScriptFileId(@Param("scriptFileId") Long scriptFileId);

    List<GlobalScriptSnapshotModel> findByNodeId(@Param("nodeId") Long nodeId);

    int insert(GlobalScriptSnapshotModel model);

    int update(GlobalScriptSnapshotModel model);

    int deleteById(@Param("id") Long id);

    int deleteByScriptFileId(@Param("scriptFileId") Long scriptFileId);
}
