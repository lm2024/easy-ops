package com.ops.server.controller;

import com.ops.common.model.VersionModel;
import com.ops.server.mapper.VersionPackageMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VersionControllerTest extends BaseControllerTest {

    @MockBean
    private VersionPackageMapper versionPackageMapper;

    private VersionModel mockVersion() {
        VersionModel v = new VersionModel();
        v.setId(1L);
        v.setProjectId(1L);
        v.setJarName("app-1.0.jar");
        v.setFilePath("./data/versions/1/v1/app-1.0.jar");
        v.setFileSize(1024L);
        v.setVersion("v1");
        v.setSha256("abc123");
        return v;
    }

    @Test
    void listVersions() throws Exception {
        when(versionPackageMapper.findByProjectId(1L, 1, 20)).thenReturn(Arrays.asList(mockVersion()));
        when(versionPackageMapper.countByProjectId(1L)).thenReturn(1L);

        mockMvc.perform(get("/versions").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void listVersions_empty() throws Exception {
        when(versionPackageMapper.findByProjectId(1L, 1, 20)).thenReturn(Collections.emptyList());
        when(versionPackageMapper.countByProjectId(1L)).thenReturn(0L);

        mockMvc.perform(get("/versions").param("projectId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getVersion_found() throws Exception {
        when(versionPackageMapper.findById(1L)).thenReturn(mockVersion());

        mockMvc.perform(get("/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void getVersion_notFound() throws Exception {
        when(versionPackageMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/versions/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void deleteVersion() throws Exception {
        when(versionPackageMapper.findById(1L)).thenReturn(mockVersion());
        when(versionPackageMapper.deleteById(1L)).thenReturn(1);

        mockMvc.perform(delete("/versions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void deleteVersion_notFound() throws Exception {
        when(versionPackageMapper.findById(999L)).thenReturn(null);

        mockMvc.perform(delete("/versions/999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004));
    }
}
