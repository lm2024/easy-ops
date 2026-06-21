package com.ops.server.mapper;

import com.ops.common.model.NodeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NodeMapperTest {

    @Autowired
    private NodeMapper nodeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM node_info");
    }

    @Test
    @DisplayName("findById - 返回节点")
    void findById_returnsNode() {
        NodeModel node = createNode("node1", "192.168.1.1", 1);
        nodeMapper.insert(node);

        NodeModel found = nodeMapper.findById(node.getId());
        assertNotNull(found);
        assertEquals("node1", found.getName());
        assertEquals("192.168.1.1", found.getIp());
    }

    @Test
    @DisplayName("findByName - 按名称查找")
    void findByName_returnsNode() {
        nodeMapper.insert(createNode("search-node", "10.0.0.1", 1));
        NodeModel found = nodeMapper.findByName("search-node");
        assertNotNull(found);
        assertEquals("search-node", found.getName());
    }

    @Test
    @DisplayName("getNodeIdByToken - 通过token获取节点ID")
    void getNodeIdByToken_returnsId() {
        NodeModel node = createNode("token-node", "10.0.0.2", 1);
        node.setToken("my-token-123");
        nodeMapper.insert(node);

        String nodeId = nodeMapper.getNodeIdByToken("my-token-123");
        assertNotNull(nodeId);
        assertEquals(node.getId().toString(), nodeId);
    }

    @Test
    @DisplayName("countByStatus - 返回节点计数")
    void countByStatus_returnsCount() {
        assertEquals(0L, nodeMapper.countByStatus(null, null));
        nodeMapper.insert(createNode("c1", "1.1.1.1", 1));
        nodeMapper.insert(createNode("c2", "2.2.2.2", 1));
        assertEquals(2L, nodeMapper.countByStatus(null, null));
    }

    @Test
    @DisplayName("updateHeartbeat - 更新心跳信息")
    void updateHeartbeat_modifiesNode() {
        NodeModel node = createNode("hb-node", "10.0.0.1", 1);
        nodeMapper.insert(node);
        long now = System.currentTimeMillis();

        nodeMapper.updateHeartbeat(node.getId(), now, "10.0.0.1", "Linux 5.4",
                "1.8.0_292", 4, 8192, 256000L, "amd64");

        NodeModel found = nodeMapper.findById(node.getId());
        assertNotNull(found.getLastHeartbeat());
        assertTrue(found.getLastHeartbeat() > 0);
        assertNotNull(found.getOsInfo());
    }

    @Test
    @DisplayName("updateStatusOffline - 标记离线(status=0)")
    void updateStatusOffline_setsOffline() {
        NodeModel node = createNode("offline-node", "10.0.0.1", 1);
        nodeMapper.insert(node);
        nodeMapper.updateStatusOffline(node.getId());

        NodeModel found = nodeMapper.findById(node.getId());
        assertEquals(0, found.getStatus());
    }

    @Test
    @DisplayName("updateTags - 更新标签")
    void updateTags_modifiesNode() {
        NodeModel node = createNode("tag-node", "10.0.0.1", 1);
        nodeMapper.insert(node);
        nodeMapper.updateTags(node.getId(), "prod,web", System.currentTimeMillis());

        NodeModel found = nodeMapper.findById(node.getId());
        assertNotNull(found.getTags());
    }

    private NodeModel createNode(String name, String ip, int status) {
        NodeModel node = new NodeModel();
        node.setName(name);
        node.setIp(ip);
        node.setPort(2123);
        node.setStatus(status);
        node.setToken("token-" + name);
        node.setOsInfo("Linux");
        node.setJavaVersion("1.8.0_292");
        node.setCreateTime(System.currentTimeMillis());
        return node;
    }
}
