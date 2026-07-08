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
class ProjectMapperTest {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM project_info");
    }

    @Test
    @DisplayName("findById - 返回正确项目")
    void findById_returnsCorrectProject() {
        ProjectModel project = createProject("my-project", 1);
        projectMapper.insert(project);

        ProjectModel found = projectMapper.findById(project.getId());
        assertNotNull(found);
        assertEquals("my-project", found.getName());
        assertEquals(1, found.getStatus());
    }

    @Test
    @DisplayName("findByName - 按名称查找")
    void findByName_returnsProject() {
        projectMapper.insert(createProject("search-pj", 0));
        ProjectModel found = projectMapper.findByName("search-pj");
        assertNotNull(found);
        assertEquals("search-pj", found.getName());
    }

    @Test
    @DisplayName("insert - 自增ID")
    void insert_generatesId() {
        ProjectModel project = createProject("insert-pj", 1);
        int rows = projectMapper.insert(project);
        assertEquals(1, rows);
        assertNotNull(project.getId());
    }

    @Test
    @DisplayName("update - 更新项目")
    void update_modifiesProject() {
        ProjectModel project = createProject("update-pj", 1);
        projectMapper.insert(project);
        project.setName("updated-pj");
        project.setStatus(0);
        int rows = projectMapper.update(project);
        assertEquals(1, rows);

        ProjectModel found = projectMapper.findById(project.getId());
        assertEquals("updated-pj", found.getName());
        assertEquals(0, found.getStatus());
    }

    @Test
    @DisplayName("deleteById - 删除项目")
    void deleteById_removesProject() {
        ProjectModel project = createProject("delete-pj", 1);
        projectMapper.insert(project);
        assertEquals(1, projectMapper.deleteById(project.getId()));
        assertNull(projectMapper.findById(project.getId()));
    }

    private ProjectModel createProject(String name, int status) {
        ProjectModel project = new ProjectModel();
        project.setName(name);
        project.setNodeIds("");
        project.setStartScript("");
        project.setStopScript("");
        project.setJarName("app.jar");
        project.setDeployDir("/opt/app");
        project.setStatus(status);
        project.setCreateTime(System.currentTimeMillis());
        return project;
    }
}
