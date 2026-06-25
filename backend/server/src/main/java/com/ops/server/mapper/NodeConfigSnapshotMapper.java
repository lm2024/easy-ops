package com.ops.server.mapper;

import com.ops.common.model.NodeConfigSnapshotModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 节点配置快照 Mapper
 */
@Mapper
public interface NodeConfigSnapshotMapper {

    List<NodeConfigSnapshotModel> findByProjectAndFile(@Param("projectId") Long projectId,
                                                       @Param("configFileId") Long configFileId);

    NodeConfigSnapshotModel findByNodeAndFile(@Param("nodeId") Long nodeId,
                                             @Param("configFileId") Long configFileId);

    int insert(NodeConfigSnapshotModel model);

    int update(NodeConfigSnapshotModel model);
}
