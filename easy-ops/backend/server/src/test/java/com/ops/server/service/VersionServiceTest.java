package com.ops.server.service;

import com.ops.common.model.VersionModel;
import com.ops.server.mapper.VersionPackageMapper;
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
class VersionServiceTest {

    @Mock
    private VersionPackageMapper versionPackageMapper;

    @InjectMocks
    private VersionService versionService;

    private VersionModel sampleVersion;

    @BeforeEach
    void setUp() {
        sampleVersion = new VersionModel();
        sampleVersion.setId(1L);
        sampleVersion.setProjectId(10L);
        sampleVersion.setVersion("1.0.0");
        sampleVersion.setJarName("app.jar");
        sampleVersion.setFilePath("/data/versions/10/1/app.jar");
        sampleVersion.setFileSize(1024L);
        sampleVersion.setSha256("abc123");
        sampleVersion.setCreateTime(System.currentTimeMillis());
    }

    @Test
    @DisplayName("findById - 返回正确的版本")
    void findById_returnsCorrectVersion() {
        when(versionPackageMapper.findById(1L)).thenReturn(sampleVersion);

        VersionModel result = versionService.findById(1L);

        assertNotNull(result);
        assertEquals("1.0.0", result.getVersion());
        assertEquals("app.jar", result.getJarName());
    }

    @Test
    @DisplayName("findById - 不存在返回null")
    void findById_returnsNull() {
        when(versionPackageMapper.findById(999L)).thenReturn(null);

        VersionModel result = versionService.findById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("getProjectNameByProjectId - 返回项目名")
    void getProjectNameByProjectId_returnsProjectName() {
        when(versionPackageMapper.getProjectNameByProjectId(10L)).thenReturn("test-project");

        String name = versionService.getProjectNameByProjectId(10L);
        assertEquals("test-project", name);
    }

    @Test
    @DisplayName("getProjectNameByProjectId - 无结果时返回null")
    void getProjectNameByProjectId_null() {
        when(versionPackageMapper.getProjectNameByProjectId(999L)).thenReturn(null);

        String name = versionService.getProjectNameByProjectId(999L);
        assertNull(name);
    }

    @Test
    @DisplayName("findByProjectId - 返回项目的版本列表")
    void findByProjectId_returnsVersions() {
        List<VersionModel> expected = Arrays.asList(
            createVersion(1L, 10L, "1.0.0"),
            createVersion(2L, 10L, "2.0.0")
        );
        when(versionPackageMapper.findByProjectId(10L, 0, 20)).thenReturn(expected);

        List<VersionModel> result = versionService.findByProjectId(10L, 0, 20);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("1.0.0", result.get(0).getVersion());
        assertEquals("2.0.0", result.get(1).getVersion());
    }

    @Test
    @DisplayName("countByProjectId - 返回版本计数")
    void countByProjectId_returnsCount() {
        when(versionPackageMapper.countByProjectId(10L)).thenReturn(5L);

        Long count = versionService.countByProjectId(10L);
        assertEquals(5L, count);
    }

    @Test
    @DisplayName("insert - 返回插入行数")
    void insert_returnsInsertCount() {
        when(versionPackageMapper.insert(sampleVersion)).thenReturn(1);

        int result = versionService.insert(sampleVersion);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("deleteById - 返回删除行数")
    void deleteById_returnsDeleteCount() {
        when(versionPackageMapper.deleteById(1L)).thenReturn(1);

        int result = versionService.deleteById(1L);
        assertEquals(1, result);
    }

    private VersionModel createVersion(Long id, Long projectId, String version) {
        VersionModel v = new VersionModel();
        v.setId(id);
        v.setProjectId(projectId);
        v.setVersion(version);
        v.setJarName("app.jar");
        v.setFilePath("/data/versions/" + projectId + "/" + id + "/app.jar");
        v.setFileSize(1024L);
        v.setSha256("sha256-" + id);
        v.setCreateTime(System.currentTimeMillis());
        return v;
    }
}
