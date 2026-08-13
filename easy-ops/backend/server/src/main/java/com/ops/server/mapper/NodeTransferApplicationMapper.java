package com.ops.server.mapper;

import com.ops.common.model.NodeTransferApplicationModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NodeTransferApplicationMapper {
    int insert(NodeTransferApplicationModel app);
    NodeTransferApplicationModel findById(@Param("id") Long id);
    NodeTransferApplicationModel findByNodeIdAndStatus(@Param("nodeId") Long nodeId, @Param("status") String status);
    /** 平台管理员：全量（可按状态筛选） */
    List<NodeTransferApplicationModel> listAll(@Param("status") String status);
    /** 租户：本租户相关的申请（可按状态筛选） */
    List<NodeTransferApplicationModel> listByTenant(@Param("tenantId") Long tenantId, @Param("status") String status);
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("approveTime") Long approveTime, @Param("approverId") Long approverId,
                     @Param("approverUsername") String approverUsername, @Param("updateTime") Long updateTime);
    long countPending();
}
