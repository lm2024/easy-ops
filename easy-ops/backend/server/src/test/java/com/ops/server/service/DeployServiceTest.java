package com.ops.server.service;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.server.mapper.DeployRecordMapper;
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
class DeployServiceTest {

    @Mock
    private DeployRecordMapper deployRecordMapper;

    @InjectMocks
    private DeployService deployService;

    private DeployModel sampleDeploy;

    @BeforeEach
    void setUp() {
        sampleDeploy = new DeployModel();
        sampleDeploy.setId(1L);
        sampleDeploy.setProjectId(10L);
        sampleDeploy.setVersionId(100L);
        sampleDeploy.setNodeId(20L);
        sampleDeploy.setStatus(DeployStatus.SCHEDULED.getCode());
        sampleDeploy.setJarName("app.jar");
        sampleDeploy.setScheduleTime(System.currentTimeMillis());
    }

    @Test
    @DisplayName("findById - 返回正确的部署记录")
    void findById_returnsCorrectDeploy() {
        when(deployRecordMapper.findById(1L)).thenReturn(sampleDeploy);

        DeployModel result = deployService.findById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(10L, result.getProjectId());
        verify(deployRecordMapper).findById(1L);
    }

    @Test
    @DisplayName("findById - 不存在返回null")
    void findById_returnsNullWhenNotFound() {
        when(deployRecordMapper.findById(999L)).thenReturn(null);

        DeployModel result = deployService.findById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("findByProjectId - 返回项目的部署列表")
    void findByProjectId_returnsProjectDeploys() {
        List<DeployModel> expected = Arrays.asList(
            createDeploy(1L, 10L),
            createDeploy(2L, 10L)
        );
        when(deployRecordMapper.findByProjectId(10L, 0, 20)).thenReturn(expected);

        List<DeployModel> result = deployService.findByProjectId(10L, 0, 20);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getProjectId());
        verify(deployRecordMapper).findByProjectId(10L, 0, 20);
    }

    @Test
    @DisplayName("countByProjectId - 返回项目部署计数")
    void countByProjectId_returnsCount() {
        when(deployRecordMapper.countByProjectId(10L)).thenReturn(7L);

        Long count = deployService.countByProjectId(10L);
        assertEquals(7L, count);
    }

    @Test
    @DisplayName("insert - 返回插入行数")
    void insert_returnsInsertCount() {
        when(deployRecordMapper.insert(sampleDeploy)).thenReturn(1);

        int result = deployService.insert(sampleDeploy);
        assertEquals(1, result);
        verify(deployRecordMapper).insert(sampleDeploy);
    }

    private DeployModel createDeploy(Long id, Long projectId) {
        DeployModel deploy = new DeployModel();
        deploy.setId(id);
        deploy.setProjectId(projectId);
        deploy.setVersionId(id * 100L);
        deploy.setNodeId(id * 20L);
        deploy.setStatus(DeployStatus.SCHEDULED.getCode());
        deploy.setJarName("app.jar");
        deploy.setScheduleTime(System.currentTimeMillis());
        return deploy;
    }
}
