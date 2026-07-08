package com.ops.server.controller;

import com.ops.server.knowledge.service.KnowledgeCategoryService;
import com.ops.server.knowledge.service.KnowledgeDocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeControllerTest extends BaseControllerTest {

    @MockBean
    private KnowledgeCategoryService categoryService;
    @MockBean
    private KnowledgeDocumentService documentService;

    @Test
    void listCategories() throws Exception {
        when(categoryService.getCategoryTree(null)).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/kb/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void search() throws Exception {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("list", Collections.emptyList());
        result.put("total", 0L);
        when(documentService.search(eq("OOM"), anyInt(), anyInt())).thenReturn(result);

        mockMvc.perform(get("/kb/search").param("q", "OOM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
