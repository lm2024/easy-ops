package com.ops.server.mapper;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DeployRecordMapperTest {

    @Autowired
    private DeployRecordMapper deployRecordMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM deploy_record");
    }

    @Test
    @DisplayName("findById - 返回部署记录")
    void findById_returnsDeploy() {
        DeployModel deploy = createDeploy(1L, 1L, 1L, 0);
        deployRecordMapper.insert(deploy);

        DeployModel found = deployRecordMapper.findById(deploy.getId());
        assertNotNull(found);
        assertEquals(1L, found.getProjectId());
        assertEquals(0, found.getStatus());
    }

    @Test
    @DisplayName("insert - 自增ID")
    void insert_generatesId() {
        DeployModel deploy = createDeploy(5L, 1L, 1L, 0);
        int rows = deployRecordMapper.insert(deploy);
        assertEquals(1, rows);
        assertNotNull(deploy.getId());
    }

    @Test
    @DisplayName("updateStatus - 更新状态和日志")
    void updateStatus_modifiesRecord() {
        DeployModel deploy = createDeploy(30L, 1L, 1L, 0);
        deployRecordMapper.insert(deploy);

        long now = System.currentTimeMillis();
        deployRecordMapper.updateStatus(deploy.getId(), 1, "deploy complete", now);

        DeployModel found = deployRecordMapper.findById(deploy.getId());
        assertEquals(1, found.getStatus());
        assertNotNull(found.getLog());
        assertNotNull(found.getEndTime());
        assertTrue(found.getEndTime() > 0);
    }

    @Test
    @DisplayName("findScheduledReady - 查找已计划可执行的部署")
    void findScheduledReady_returnsScheduled() {
        DeployModel deploy = createDeploy(50L, 1L, 1L, 5);
        deploy.setScheduleTime(System.currentTimeMillis() - 1000L);
        deployRecordMapper.insert(deploy);

        List<DeployModel> result = deployRecordMapper.findScheduledReady(System.currentTimeMillis());
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    private DeployModel createDeploy(Long projectId, Long versionId, Long nodeId, int status) {
        DeployModel deploy = new DeployModel();
        deploy.setProjectId(projectId);
        deploy.setVersionId(versionId);
        deploy.setNodeId(nodeId);
        deploy.setStatus(status);
        deploy.setJarName("app.jar");
        deploy.setStartTime(System.currentTimeMillis());
        deploy.setCreateTime(System.currentTimeMillis());
        return deploy;
    }
}
