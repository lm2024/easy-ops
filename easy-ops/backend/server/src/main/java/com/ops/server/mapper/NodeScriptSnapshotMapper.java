package com.ops.server.mapper;

import com.ops.common.model.NodeScriptSnapshotModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点脚本快照 Mapper
 */
@Mapper
public interface NodeScriptSnapshotMapper {

    NodeScriptSnapshotModel findById(@Param("id") Long id);

    NodeScriptSnapshotModel findByNodeAndFile(@Param("nodeId") Long nodeId, @Param("scriptFileId") Long scriptFileId);

    List<NodeScriptSnapshotModel> findByProjectAndFile(@Param("projectId") Long projectId, @Param("scriptFileId") Long scriptFileId);

    List<NodeScriptSnapshotModel> findByNodeId(@Param("nodeId") Long nodeId);

    int insert(NodeScriptSnapshotModel model);

    int update(NodeScriptSnapshotModel model);

    int deleteById(@Param("id") Long id);

    int deleteByScriptFileId(@Param("scriptFileId") Long scriptFileId);

    int deleteByProjectId(@Param("projectId") Long projectId);
}
