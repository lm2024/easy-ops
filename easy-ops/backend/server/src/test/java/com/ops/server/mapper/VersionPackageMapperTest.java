package com.ops.server.mapper;

import com.ops.common.model.VersionModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class VersionPackageMapperTest {

    @Autowired
    private VersionPackageMapper versionPackageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM version_package");
    }

    @Test
    @DisplayName("findById - 返回版本")
    void findById_returnsVersion() {
        VersionModel version = createVersion(1L, "1.0.0");
        versionPackageMapper.insert(version);

        VersionModel found = versionPackageMapper.findById(version.getId());
        assertNotNull(found);
        assertEquals("1.0.0", found.getVersion());
    }

    @Test
    @DisplayName("insert - 自增ID")
    void insert_generatesId() {
        VersionModel version = createVersion(1L, "0.1.0");
        int rows = versionPackageMapper.insert(version);
        assertEquals(1, rows);
        assertNotNull(version.getId());
    }

    @Test
    @DisplayName("deleteById - 删除版本")
    void deleteById_removesVersion() {
        VersionModel version = createVersion(1L, "0.1.0");
        versionPackageMapper.insert(version);
        assertEquals(1, versionPackageMapper.deleteById(version.getId()));
        assertNull(versionPackageMapper.findById(version.getId()));
    }

    @Test
    @DisplayName("getLastId - 返回最新ID")
    void getLastId_returnsLatest() {
        assertNull(versionPackageMapper.getLastId());
        versionPackageMapper.insert(createVersion(1L, "1.0.0"));
        versionPackageMapper.insert(createVersion(1L, "2.0.0"));
        Long lastId = versionPackageMapper.getLastId();
        assertNotNull(lastId);
        assertTrue(lastId > 0);
    }

    private VersionModel createVersion(Long projectId, String version) {
        VersionModel versionModel = new VersionModel();
        versionModel.setProjectId(projectId);
        versionModel.setJarName("app.jar");
        versionModel.setFilePath("/tmp/app.jar");
        versionModel.setFileSize(1024L);
        versionModel.setVersion(version);
        versionModel.setCreateTime(System.currentTimeMillis());
        return versionModel;
    }
}
