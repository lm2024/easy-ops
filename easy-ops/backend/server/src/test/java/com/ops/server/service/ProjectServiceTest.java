package com.ops.server.service;

import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ProjectMapper;
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
class ProjectServiceTest {

    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectService projectService;

    private ProjectModel sampleProject;

    @BeforeEach
    void setUp() {
        sampleProject = new ProjectModel();
        sampleProject.setId(1L);
        sampleProject.setName("test-project");
        sampleProject.setStatus(1); // RUNNING
        sampleProject.setNodeIds("1");
        sampleProject.setStartScript("java -jar app.jar");
        sampleProject.setStopScript("curl -s http://localhost:8080/stop");
    }

    @Test
    @DisplayName("findById - 返回正确的项目")
    void findById_returnsCorrectProject() {
        when(projectMapper.findById(1L)).thenReturn(sampleProject);

        ProjectModel result = projectService.findById(1L);

        assertNotNull(result);
        assertEquals("test-project", result.getName());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("findById - 不存在返回null")
    void findById_returnsNull() {
        when(projectMapper.findById(999L)).thenReturn(null);

        ProjectModel result = projectService.findById(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("findByName - 按名称查找")
    void findByName_returnsProject() {
        when(projectMapper.findByName("test-project")).thenReturn(sampleProject);

        ProjectModel result = projectService.findByName("test-project");

        assertNotNull(result);
        assertEquals("test-project", result.getName());
    }

    @Test
    @DisplayName("findByFilters - 返回符合条件的项目列表")
    void findByFilters_returnsMatchingProjects() {
        List<ProjectModel> expected = Arrays.asList(
            createProject(1L, 1),
            createProject(2L, 0)
        );
        when(projectMapper.findByFilters("RUNNING", 1L, 0, 20)).thenReturn(expected);

        List<ProjectModel> result = projectService.findByFilters("RUNNING", 1L, 0, 20);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("project-1", result.get(0).getName());
    }

    @Test
    @DisplayName("countByFilters - 返回项目计数")
    void countByFilters_returnsCount() {
        when(projectMapper.countByFilters("RUNNING", 1L)).thenReturn(3L);

        Long count = projectService.countByFilters("RUNNING", 1L);
        assertEquals(3L, count);
    }

    @Test
    @DisplayName("countByFilters - nodeId为null时也能工作")
    void countByFilters_nodeIdNull() {
        when(projectMapper.countByFilters(null, null)).thenReturn(10L);

        Long count = projectService.countByFilters(null, null);
        assertEquals(10L, count);
    }

    @Test
    @DisplayName("insert - 返回插入行数")
    void insert_returnsInsertCount() {
        when(projectMapper.insert(sampleProject)).thenReturn(1);

        int result = projectService.insert(sampleProject);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("update - 返回更新行数")
    void update_returnsUpdateCount() {
        when(projectMapper.update(sampleProject)).thenReturn(1);

        int result = projectService.update(sampleProject);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("deleteById - 返回删除行数")
    void deleteById_returnsDeleteCount() {
        when(projectMapper.deleteById(1L)).thenReturn(1);

        int result = projectService.deleteById(1L);
        assertEquals(1, result);
    }

    @Test
    @DisplayName("deleteById - 不存在的节点返回0")
    void deleteById_nonExisting_returnsZero() {
        when(projectMapper.deleteById(999L)).thenReturn(0);

        int result = projectService.deleteById(999L);
        assertEquals(0, result);
    }

    private ProjectModel createProject(Long id, int status) {
        ProjectModel project = new ProjectModel();
        project.setId(id);
        project.setName("project-" + id);
        project.setStatus(status);
        project.setNodeIds(String.valueOf(id));
        project.setStartScript("java -jar app.jar");
        project.setStopScript("curl -s http://localhost:8080/stop");
        return project;
    }
}
