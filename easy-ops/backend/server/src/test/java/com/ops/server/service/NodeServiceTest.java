package com.ops.server.service;

import com.ops.common.model.NodeModel;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    private NodeMapper nodeMapper;

    @InjectMocks
    private NodeService nodeService;

    private NodeModel sampleNode;

    @BeforeEach
    void setUp() {
        sampleNode = new NodeModel();
        sampleNode.setId(1L);
        sampleNode.setName("test-node");
        sampleNode.setIp("192.168.1.100");
        sampleNode.setPort(2123);
        sampleNode.setStatus(1); // ONLINE = 1
        sampleNode.setToken("test-token-123");
        sampleNode.setTags("tag1,tag2");
    }

    @Test
    @DisplayName("findById - 返回正确的节点")
    void findById_returnsCorrectNode() {
        when(nodeMapper.findById(1L)).thenReturn(sampleNode);

        NodeModel result = nodeService.findById(1L);

        assertNotNull(result);
        assertEquals("test-node", result.getName());
        assertEquals("192.168.1.100", result.getIp());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("findByName - 按名称查找节点")
    void findByName_returnsNode() {
        when(nodeMapper.findByName("test-node")).thenReturn(sampleNode);

        NodeModel result = nodeService.findByName("test-node");

        assertNotNull(result);
        assertEquals("test-node", result.getName());
    }

    @Test
    @DisplayName("findByName - 不存在返回null")
    void findByName_returnsNull() {
        when(nodeMapper.findByName("unknown")).thenReturn(null);

        NodeModel result = nodeService.findByName("unknown");
        assertNull(result);
    }

    @Test
    @DisplayName("findByStatus - 按状态筛选节点列表")
    void findByStatus_returnsFilteredNodes() {
        List<NodeModel> expected = Arrays.asList(
            createNode(1L, 1),
            createNode(2L, 1)
        );
        when(nodeMapper.findByStatus("ONLINE", 0, 20, null)).thenReturn(expected);

        List<NodeModel> result = nodeService.findByStatus("ONLINE", 0, 20, null);

        assertNotNull(result);
        assertEquals(2, result.size());
        verify(nodeMapper).findByStatus("ONLINE", 0, 20, null);
    }

    @Test
    @DisplayName("findByStatus - 支持关键词过滤")
    void findByStatus_withKeyword() {
        when(nodeMapper.findByStatus("ONLINE", 0, 10, "test"))
            .thenReturn(Collections.singletonList(createNode(1L, 1)));

        List<NodeModel> result = nodeService.findByStatus("ONLINE", 0, 10, "test");

        assertEquals(1, result.size());
        verify(nodeMapper).findByStatus("ONLINE", 0, 10, "test");
    }

    @Test
    @DisplayName("countByStatus - 返回节点计数")
    void countByStatus_returnsCount() {
        when(nodeMapper.countByStatus("ONLINE", null)).thenReturn(5L);

        Long count = nodeService.countByStatus("ONLINE", null);
        assertEquals(5L, count);
    }

    @Test
    @DisplayName("insert - 返回插入行数")
    void insert_returnsInsertCount() {
        when(nodeMapper.insert(sampleNode)).thenReturn(1);

        int result = nodeService.insert(sampleNode);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("update - 返回更新行数")
    void update_returnsUpdateCount() {
        when(nodeMapper.update(sampleNode)).thenReturn(1);

        int result = nodeService.update(sampleNode);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("deleteById - 返回删除行数")
    void deleteById_returnsDeleteCount() {
        when(nodeMapper.deleteById(1L)).thenReturn(1);

        int result = nodeService.deleteById(1L);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("deleteById - 不存在的节点返回0")
    void deleteById_nonExisting_returnsZero() {
        when(nodeMapper.deleteById(999L)).thenReturn(0);

        int result = nodeService.deleteById(999L);
        assertEquals(0, result);
    }

    @Test
    @DisplayName("countByNodeId - 返回关联数量")
    void countByNodeId_returnsCount() {
        when(nodeMapper.countByNodeId(1L)).thenReturn(3);

        int result = nodeService.countByNodeId(1L);
        assertEquals(3, result);
    }

    @Test
    @DisplayName("updateHeartbeat - 调用Mapper更新心跳")
    void updateHeartbeat_callsMapper() {
        nodeService.updateHeartbeat(
            1L, System.currentTimeMillis(), "192.168.1.1", "Linux 5.4",
            "1.8.0_292", 8, 16384, 512000L, "amd64", "1.0.0-SNAPSHOT"
        );

        verify(nodeMapper).updateHeartbeat(
            eq(1L), anyLong(), eq("192.168.1.1"), eq("Linux 5.4"),
            eq("1.8.0_292"), eq(8), eq(16384), eq(512000L), eq("amd64"), eq("1.0.0-SNAPSHOT")
        );
    }

    @Test
    @DisplayName("updateTags - 调用Mapper更新标签")
    void updateTags_callsMapper() {
        nodeService.updateTags(1L, "new,tag");
        verify(nodeMapper).updateTags(eq(1L), eq("new,tag"), anyLong());
    }

    private NodeModel createNode(Long id, int status) {
        NodeModel node = new NodeModel();
        node.setId(id);
        node.setName("node-" + id);
        node.setIp("192.168.1." + id);
        node.setPort(2123);
        node.setStatus(status);
        node.setToken("token-" + id);
        node.setTags("");
        return node;
    }
}
