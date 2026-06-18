package com.ops.server.service;

import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NodeService {
    @Autowired
    private NodeMapper nodeMapper;

    public NodeModel findById(Long id) { return nodeMapper.findById(id); }
    public NodeModel findByName(String name) { return nodeMapper.findByName(name); }
    public List<NodeModel> findByStatus(String status, Integer page, Integer pageSize, String keyword) {
        return nodeMapper.findByStatus(status, page, pageSize, keyword);
    }
    public Long countByStatus(String status, String keyword) { return nodeMapper.countByStatus(status, keyword); }
    public int insert(NodeModel node) { return nodeMapper.insert(node); }
    public int update(NodeModel node) { return nodeMapper.update(node); }
    public int deleteById(Long id) { return nodeMapper.deleteById(id); }
    public void updateHeartbeat(Long id, Long lastHeartbeat, String ip, String osInfo, String javaVersion) {
        nodeMapper.updateHeartbeat(id, lastHeartbeat, ip, osInfo, javaVersion);
    }
}
