package com.ops.server.controller;

import com.ops.common.model.NodeModel;
import com.ops.common.response.Result;
import com.ops.server.mapper.NodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentProxyControllerTest {

    @Mock
    private NodeMapper nodeMapper;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AgentProxyController controller;

    @Test
    @DisplayName("getSysInfo - 节点不存在返回错误")
    void getSysInfo_nodeNotFound() {
        when(nodeMapper.findById(999L)).thenReturn(null);

        Object result = controller.getSysInfo("999");
        assertEquals(1002, ((Result<?>) result).getCode());
    }

    @Test
    @DisplayName("getSysInfo - 正常请求返回节点信息")
    void getSysInfo_validRequest() {
        NodeModel node = new NodeModel();
        node.setId(1L);
        node.setIp("127.0.0.1");
        node.setPort(2123);
        when(nodeMapper.findById(1L)).thenReturn(node);

        Object result = controller.getSysInfo("1");
        assertNotNull(result);
    }
}
