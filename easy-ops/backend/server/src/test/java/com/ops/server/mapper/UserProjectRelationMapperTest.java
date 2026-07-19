package com.ops.server.mapper;

import com.ops.common.model.ProjectModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserProjectRelationMapperTest {

    @Autowired
    private UserProjectRelationMapper userProjectRelationMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM user_project_relation");
        jdbcTemplate.execute("DELETE FROM project_info");
    }

    @Test
    @DisplayName("insert - 插入用户-项目关系")
    void insert_createsRelation() {
        int rows = userProjectRelationMapper.insert(1L, 10L, System.currentTimeMillis());
        assertEquals(1, rows);

        Long count = userProjectRelationMapper.countByUserIdAndProjectId(1L, 10L);
        assertEquals(1L, count);
    }

    @Test
    @DisplayName("findProjectIdsByUserId - 查询用户可访问的项目")
    void findProjectIdsByUserId_returnsProjectIds() {
        userProjectRelationMapper.insert(1L, 100L, System.currentTimeMillis());
        userProjectRelationMapper.insert(1L, 200L, System.currentTimeMillis());

        List<Long> projectIds = userProjectRelationMapper.findProjectIdsByUserId(1L);
        assertEquals(2, projectIds.size());
        assertTrue(projectIds.contains(100L));
        assertTrue(projectIds.contains(200L));
    }

    @Test
    @DisplayName("countByUserIdAndProjectId - 检查权限")
    void countByUserIdAndProjectId_returnsCorrectCount() {
        assertEquals(0L, userProjectRelationMapper.countByUserIdAndProjectId(999L, 999L));

        userProjectRelationMapper.insert(1L, 50L, System.currentTimeMillis());
        assertEquals(1L, userProjectRelationMapper.countByUserIdAndProjectId(1L, 50L));
        assertEquals(0L, userProjectRelationMapper.countByUserIdAndProjectId(2L, 50L));
    }

    @Test
    @DisplayName("findAllProjectIds - 返回所有项目ID (admin)")
    void findAllProjectIds_returnsAll() {
        ProjectModel project = new ProjectModel();
        project.setName("test-project");
        project.setJarName("app.jar");
        projectMapper.insert(project);

        // After insert, the auto-generated id is set on the project object
        Long insertedProjectId = project.getId();

        userProjectRelationMapper.insert(1L, insertedProjectId, System.currentTimeMillis());
        List<Long> projectIds = userProjectRelationMapper.findAllProjectIds();
        assertNotNull(projectIds);
        assertTrue(projectIds.contains(insertedProjectId));
    }

    @Test
    @DisplayName("deleteByUserIdAndProjectId - 删除关系")
    void deleteByUserIdAndProjectId_removesRelation() {
        userProjectRelationMapper.insert(1L, 300L, System.currentTimeMillis());
        assertEquals(1, userProjectRelationMapper.deleteByUserIdAndProjectId(1L, 300L));

        Long count = userProjectRelationMapper.countByUserIdAndProjectId(1L, 300L);
        assertEquals(0L, count);
    }
}
