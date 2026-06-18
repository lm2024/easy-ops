package com.ops.server.mapper;

import com.ops.common.model.NodeModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NodeMapper {
    NodeModel findById(@Param("id") Long id);
    NodeModel findByName(@Param("name") String name);
    String getNodeIdByToken(@Param("token") String token);
    String getTokenByToken(@Param("token") String token);
    List<NodeModel> findByStatus(@Param("status") String status, @Param("page") Integer page,
                                  @Param("pageSize") Integer pageSize, @Param("keyword") String keyword);
    Long countByStatus(@Param("status") String status, @Param("keyword") String keyword);
    int insert(NodeModel node);
    int update(NodeModel node);
    int deleteById(@Param("id") Long id);
    void updateHeartbeat(@Param("id") Long id, @Param("lastHeartbeat") Long lastHeartbeat,
                         @Param("ip") String ip, @Param("osInfo") String osInfo,
                         @Param("javaVersion") String javaVersion);
    void updateStatusOffline(@Param("id") Long id);
    List<Map<String, Object>> getOfflineCandidates(@Param("cutoff") Long cutoff);
    int countByNodeId(@Param("nodeId") Long nodeId);
    List<String> getProjectNamesByNodeId(@Param("nodeId") Long nodeId);
}
