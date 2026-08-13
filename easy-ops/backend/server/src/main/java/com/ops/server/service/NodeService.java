package com.ops.server.service;

import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class NodeService {
    @Autowired
    private NodeMapper nodeMapper;

    public NodeModel findById(Long id) { return nodeMapper.findById(id); }
    public NodeModel findByName(String name) { return nodeMapper.findByName(name); }
    public List<NodeModel> findByStatus(String status, Integer page, Integer pageSize, String keyword,
                                         String sortField, String sortOrder) {
        return nodeMapper.findByStatus(status, page, pageSize, keyword, sortField, sortOrder);
    }
    public List<NodeModel> findByStatus(String status, Integer page, Integer pageSize, String keyword) {
        return nodeMapper.findByStatus(status, page, pageSize, keyword, null, null);
    }
    public Long countByStatus(String status, String keyword) { return nodeMapper.countByStatus(status, keyword); }
    public List<NodeModel> findByStatusInTenant(String status, Integer page, Integer pageSize, String keyword,
                                                String sortField, String sortOrder, Long tenantId, Long defaultTenantId, List<Long> projectIds) {
        return nodeMapper.findByStatusInTenant(status, page, pageSize, keyword, sortField, sortOrder, tenantId, defaultTenantId, projectIds);
    }
    public Long countByStatusInTenant(String status, String keyword, Long tenantId, Long defaultTenantId, List<Long> projectIds) {
        return nodeMapper.countByStatusInTenant(status, keyword, tenantId, defaultTenantId, projectIds);
    }
    public int insert(NodeModel node) { return nodeMapper.insert(node); }
    public int update(NodeModel node) { return nodeMapper.update(node); }
    public int deleteById(Long id) { return nodeMapper.deleteById(id); }
    public int countByNodeId(Long nodeId) { return nodeMapper.countByNodeId(nodeId); }
    public void updateTenant(Long id, Long tenantId) { nodeMapper.updateTenant(id, tenantId, System.currentTimeMillis()); }
    public void updateHeartbeat(Long id, Long lastHeartbeat, String ip, String osInfo, String javaVersion,
                                Integer cpuCores, Integer totalMemoryMb, Long totalDiskMb, String osArch,
                                String agentVersion, Long agentPid) {
        nodeMapper.updateHeartbeat(id, lastHeartbeat, ip, osInfo, javaVersion,
                cpuCores, totalMemoryMb, totalDiskMb, osArch, agentVersion, agentPid);
    }
    public void updateTags(Long id, String tags) {
        nodeMapper.updateTags(id, tags, System.currentTimeMillis());
    }
}
