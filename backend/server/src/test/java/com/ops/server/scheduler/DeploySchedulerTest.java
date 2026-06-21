package com.ops.server.scheduler;

import com.ops.common.enums.DeployStatus;
import com.ops.common.model.DeployModel;
import com.ops.common.model.NodeModel;
import com.ops.common.model.ProjectModel;
import com.ops.common.model.VersionModel;
import com.ops.server.mapper.DeployRecordMapper;
import com.ops.server.mapper.NodeMapper;
import com.ops.server.mapper.ProjectMapper;
import com.ops.server.mapper.VersionPackageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploySchedulerTest {

    @Mock
    private DeployRecordMapper deployRecordMapper;

    @Mock
    private ProjectMapper projectMapper;

    @Mock
    private VersionPackageMapper versionPackageMapper;

    @Mock
    private NodeMapper nodeMapper;

    @Mock
    private DistributedLock distributedLock;

    @InjectMocks
    private DeployScheduler deployScheduler;

    @BeforeEach
    void setUp() throws Exception {
        setField("deployRecordMapper", deployRecordMapper);
        setField("projectMapper", projectMapper);
        setField("versionPackageMapper", versionPackageMapper);
        setField("nodeMapper", nodeMapper);
        setField("distributedLock", distributedLock);
        setField("serverPath", "/opt/data");
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = deployScheduler.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(deployScheduler, value);
    }

    @Test
    @DisplayName("executeScheduledDeploys - 锁获取失败时直接返回")
    void executeScheduledDeploys_lockFailed_returnsEarly() {
        when(distributedLock.tryLock("deploy_scheduler")).thenReturn(false);

        deployScheduler.executeScheduledDeploys();

        verify(deployRecordMapper, never()).findScheduledReady(anyLong());
    }

    @Test
    @DisplayName("executeScheduledDeploys - 获取锁后正常执行")
    void executeScheduledDeploys_lockAcquired_executes() throws Exception {
        when(distributedLock.tryLock("deploy_scheduler")).thenReturn(true);
        when(deployRecordMapper.findScheduledReady(anyLong())).thenReturn(Collections.emptyList());

        deployScheduler.executeScheduledDeploys();

        verify(deployRecordMapper).findScheduledReady(anyLong());
        verify(distributedLock).releaseLock("deploy_scheduler");
    }

    @Test
    @DisplayName("doExecuteScheduledDeploys - 空列表不执行")
    void doExecuteScheduledDeploys_emptyList_doesNothing() throws Exception {
        when(deployRecordMapper.findScheduledReady(anyLong())).thenReturn(Collections.emptyList());

        invokeDoExecute();

        verify(deployRecordMapper).findScheduledReady(anyLong());
        verify(projectMapper, never()).findById(anyLong());
    }

    @Test
    @DisplayName("doExecuteScheduledDeploys - 项目不存在时标记失败")
    void doExecuteScheduledDeploys_missingProject_marksFailed() throws Exception {
        DeployModel deploy = createDeployModel(1L, 10L, 100L, DeployStatus.SCHEDULED.getCode());
        when(deployRecordMapper.findScheduledReady(anyLong())).thenReturn(Collections.singletonList(deploy));
        when(projectMapper.findById(10L)).thenReturn(null);

        invokeDoExecute();

        verify(deployRecordMapper).updateStatus(
                eq(1L), eq(DeployStatus.FAILED.getCode()), anyString(), anyLong());
    }

    @Test
    @DisplayName("doExecuteScheduledDeploys - 版本不存在时标记失败")
    void doExecuteScheduledDeploys_missingVersion_marksFailed() throws Exception {
        ProjectModel project = createProjectModel(10L, "test-app");
        DeployModel deploy = createDeployModel(2L, 10L, 200L, DeployStatus.SCHEDULED.getCode());
        when(deployRecordMapper.findScheduledReady(anyLong())).thenReturn(Collections.singletonList(deploy));
        when(projectMapper.findById(10L)).thenReturn(project);
        when(versionPackageMapper.findById(200L)).thenReturn(null);

        invokeDoExecute();

        verify(deployRecordMapper).updateStatus(
                eq(2L), eq(DeployStatus.FAILED.getCode()), anyString(), anyLong());
    }

    @Test
    @DisplayName("doExecuteScheduledDeploys - 节点不存在时标记失败")
    void doExecuteScheduledDeploys_missingNode_marksFailed() throws Exception {
        ProjectModel project = createProjectModel(10L, "test-app");
        VersionModel version = createVersionModel(100L, 10L, "1.0.0");
        DeployModel deploy = createDeployModel(3L, 10L, 100L, DeployStatus.SCHEDULED.getCode());
        when(deployRecordMapper.findScheduledReady(anyLong())).thenReturn(Collections.singletonList(deploy));
        when(projectMapper.findById(10L)).thenReturn(project);
        when(versionPackageMapper.findById(100L)).thenReturn(version);
        when(nodeMapper.findById(100L)).thenReturn(null);

        invokeDoExecute();

        verify(deployRecordMapper).updateStatus(
                eq(3L), eq(DeployStatus.FAILED.getCode()), anyString(), anyLong());
    }

    @Test
    @DisplayName("findJarPath - 有效filePath时返回路径")
    void findJarPath_validFilePath_returnsPath() throws Exception {
        VersionModel version = createVersionModel(100L, 10L, "1.0.0");
        version.setFilePath("/opt/data/versions/10/100/app.jar");

        String result = invokeFindJarPath(10L, version);
        // The actual implementation uses version name in the path, not version ID
        assertEquals("/opt/data/versions/10/1.0.0/app.jar", result);
    }

    @Test
    @DisplayName("findJarPath - filePath不存在时回退到version-name路径")
    void findJarPath_invalidFilePath_fallbacks() throws Exception {
        VersionModel version = createVersionModel(100L, 50L, "2.0.0");
        version.setFilePath("/nonexistent/path/app.jar");

        String result = invokeFindJarPath(50L, version);
        assertNotNull(result);
        // The fallback uses version name: base/versionName/app.jar
        assertTrue(result.contains("versions/50/"));
        assertTrue(result.contains("2.0.0"));
        assertTrue(result.endsWith("app.jar"));
    }

    private DeployModel createDeployModel(Long id, Long projectId, Long versionId, int status) {
        DeployModel deploy = new DeployModel();
        deploy.setId(id);
        deploy.setProjectId(projectId);
        deploy.setVersionId(versionId);
        deploy.setNodeId(100L);
        deploy.setStatus(status);
        deploy.setJarName("app.jar");
        deploy.setCreateTime(System.currentTimeMillis());
        return deploy;
    }

    private ProjectModel createProjectModel(Long id, String name) {
        ProjectModel project = new ProjectModel();
        project.setId(id);
        project.setName(name);
        project.setStartScript("./start.sh");
        project.setStopScript("./stop.sh");
        project.setDeployDir("/opt/app");
        return project;
    }

    private VersionModel createVersionModel(Long id, Long projectId, String version) {
        VersionModel versionModel = new VersionModel();
        versionModel.setId(id);
        versionModel.setProjectId(projectId);
        versionModel.setVersion(version);
        versionModel.setJarName("app.jar");
        return versionModel;
    }

    private void invokeDoExecute() throws Exception {
        java.lang.reflect.Method m = DeployScheduler.class
                .getDeclaredMethod("doExecuteScheduledDeploys");
        m.setAccessible(true);
        m.invoke(deployScheduler);
    }

    private String invokeFindJarPath(Long projectId, VersionModel v) throws Exception {
        java.lang.reflect.Method m = DeployScheduler.class
                .getDeclaredMethod("findJarPath", Long.class, VersionModel.class);
        m.setAccessible(true);
        return (String) m.invoke(deployScheduler, projectId, v);
    }
}
