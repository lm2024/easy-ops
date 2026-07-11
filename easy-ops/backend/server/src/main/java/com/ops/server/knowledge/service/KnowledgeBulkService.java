package com.ops.server.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbCategoryModel;
import com.ops.common.model.KbDocumentModel;
import com.ops.server.mapper.KbCategoryMapper;
import com.ops.server.mapper.KbDocumentMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 知识库全量导入导出服务。
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeBulkService {

    private static final String MANIFEST = "manifest.json";
    private static final String DOC_PREFIX = "documents/";
    private static final int PAGE_SIZE = 10000;

    @Autowired
    private KbCategoryMapper categoryMapper;
    @Autowired
    private KbDocumentMapper documentMapper;
    @Autowired
    private KnowledgeCategoryService categoryService;
    @Autowired
    private KnowledgeDocumentService documentService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 全量导出为 ZIP（含 manifest.json + 各分类下的 Markdown 文件）。
     */
    public StreamingResponseBody exportAll(Long projectId) throws IOException {
        List<KbCategoryModel> categories = categoryMapper.findAll(projectId);
        Map<Long, KbCategoryModel> categoryMap = toCategoryMap(categories);
        Map<Long, KbDocumentModel> allDocs = new LinkedHashMap<>();
        for (KbCategoryModel category : categories) {
            List<KbDocumentModel> docs = documentMapper.findByCategory(category.getId(), 1, PAGE_SIZE);
            for (KbDocumentModel doc : docs) {
                allDocs.put(doc.getId(), doc);
            }
        }

        List<Map<String, Object>> manifestDocs = new ArrayList<>();
        Map<String, byte[]> zipEntries = new LinkedHashMap<>();

        for (KbDocumentModel doc : allDocs.values()) {
            String categoryPath = buildCategoryPath(doc.getCategoryId(), categoryMap);
            String safeTitle = sanitizeName(doc.getTitle());
            String entryPath = DOC_PREFIX + categoryPath + "/" + safeTitle + ".md";
            String content = doc.getContent() != null ? doc.getContent() : "";
            zipEntries.put(entryPath, content.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("id", doc.getId());
            meta.put("title", doc.getTitle());
            meta.put("categoryId", doc.getCategoryId());
            meta.put("categoryPath", categoryPath);
            meta.put("file", entryPath);
            meta.put("status", doc.getStatus());
            manifestDocs.add(meta);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("version", "1.0");
        manifest.put("exportTime", System.currentTimeMillis());
        manifest.put("projectId", projectId);
        manifest.put("documentCount", manifestDocs.size());
        manifest.put("categories", buildCategoryManifest(categories, categoryMap));
        manifest.put("documents", manifestDocs);
        zipEntries.put(MANIFEST, objectMapper.writeValueAsBytes(manifest));

        return outputStream -> {
            ZipOutputStream zos = new ZipOutputStream(outputStream);
            for (Map.Entry<String, byte[]> entry : zipEntries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.finish();
            zos.flush();
        };
    }

    /**
     * 全量导入 ZIP：同分类下同标题文档覆盖，否则新增。
     */
    public Map<String, Object> importAll(MultipartFile file, Long projectId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传 ZIP 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".zip")) {
            throw new BusinessException(400, "仅支持 .zip 格式");
        }

        Map<String, byte[]> entries = readZip(file.getInputStream());
        List<ImportItem> items = parseImportItems(entries);
        if (items.isEmpty()) {
            throw new BusinessException(400, "ZIP 中未找到可导入的文档");
        }

        int created = 0;
        int updated = 0;
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, ImportItem> uniqueItems = dedupeItems(items);

        for (ImportItem item : uniqueItems.values()) {
            Long categoryId = resolveCategoryId(item.categoryPath, projectId);
            KbDocumentModel existing = documentService.findByCategoryAndTitle(categoryId, item.title);
            if (existing != null) {
                documentService.importOverwrite(existing.getId(), item.title, item.content, item.status);
                updated++;
                details.add(resultDetail(item, "updated", existing.getId()));
            } else {
                KbDocumentModel createdDoc = createImportedDoc(categoryId, item, projectId);
                created++;
                details.add(resultDetail(item, "created", createdDoc.getId()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("created", created);
        result.put("updated", updated);
        result.put("total", created + updated);
        result.put("details", details);
        return result;
    }

    private KbDocumentModel createImportedDoc(Long categoryId, ImportItem item, Long projectId) {
        KbDocumentModel doc = new KbDocumentModel();
        doc.setCategoryId(categoryId);
        doc.setTitle(item.title);
        doc.setContent(item.content);
        doc.setStatus(item.status != null ? item.status : 1);
        doc.setProjectId(projectId);
        return documentService.create(doc);
    }

    private Map<String, Object> resultDetail(ImportItem item, String action, Long docId) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("action", action);
        detail.put("documentId", docId);
        detail.put("title", item.title);
        detail.put("categoryPath", item.categoryPath);
        detail.put("file", item.filePath);
        return detail;
    }

    private Long resolveCategoryId(String categoryPath, Long projectId) {
        String path = categoryPath != null && !categoryPath.trim().isEmpty() ? categoryPath.trim() : "未分类";
        String[] parts = path.split("/");
        Long parentId = 0L;
        KbCategoryModel current = null;
        for (String part : parts) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            current = categoryMapper.findByParentAndName(parentId, name, projectId);
            if (current == null) {
                KbCategoryModel newCat = new KbCategoryModel();
                newCat.setParentId(parentId);
                newCat.setName(name);
                newCat.setProjectId(projectId);
                current = categoryService.create(newCat);
            }
            parentId = current.getId();
        }
        if (current == null) {
            current = categoryMapper.findByParentAndName(0L, "未分类", projectId);
            if (current == null) {
                KbCategoryModel newCat = new KbCategoryModel();
                newCat.setParentId(0L);
                newCat.setName("未分类");
                newCat.setProjectId(projectId);
                current = categoryService.create(newCat);
            }
        }
        return current.getId();
    }

    @SuppressWarnings("unchecked")
    private List<ImportItem> parseImportItems(Map<String, byte[]> entries) throws IOException {
        if (entries.containsKey(MANIFEST)) {
            Map<String, Object> manifest = objectMapper.readValue(entries.get(MANIFEST), Map.class);
            Object docsObj = manifest.get("documents");
            if (docsObj instanceof List) {
                List<ImportItem> items = new ArrayList<>();
                for (Object obj : (List<?>) docsObj) {
                    if (!(obj instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> meta = (Map<String, Object>) obj;
                    String filePath = meta.get("file") != null ? meta.get("file").toString() : null;
                    String title = meta.get("title") != null ? meta.get("title").toString() : null;
                    String categoryPath = meta.get("categoryPath") != null
                            ? meta.get("categoryPath").toString() : "";
                    Integer status = meta.get("status") != null
                            ? Integer.parseInt(meta.get("status").toString()) : 1;
                    if (filePath == null || !entries.containsKey(filePath)) {
                        continue;
                    }
                    if (title == null || title.trim().isEmpty()) {
                        title = fileNameToTitle(filePath);
                    }
                    String content = new String(entries.get(filePath), StandardCharsets.UTF_8);
                    items.add(new ImportItem(title.trim(), categoryPath, filePath, content, status));
                }
                if (!items.isEmpty()) {
                    return items;
                }
            }
        }
        return parseFromPaths(entries);
    }

    private List<ImportItem> parseFromPaths(Map<String, byte[]> entries) {
        List<ImportItem> items = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            String path = entry.getKey();
            if (MANIFEST.equals(path) || !path.startsWith(DOC_PREFIX) || !path.endsWith(".md")) {
                continue;
            }
            String relative = path.substring(DOC_PREFIX.length());
            int slash = relative.lastIndexOf('/');
            String categoryPath = slash > 0 ? relative.substring(0, slash) : "未分类";
            String title = fileNameToTitle(relative);
            String content = new String(entry.getValue(), StandardCharsets.UTF_8);
            items.add(new ImportItem(title, categoryPath, path, content, 1));
        }
        return items;
    }

    private Map<String, ImportItem> dedupeItems(List<ImportItem> items) {
        Map<String, ImportItem> map = new LinkedHashMap<>();
        for (ImportItem item : items) {
            String key = item.categoryPath + "\0" + item.title;
            map.put(key, item);
        }
        return map;
    }

    private String fileNameToTitle(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        if (name.endsWith(".md")) {
            name = name.substring(0, name.length() - 3);
        }
        return name;
    }

    private Map<String, byte[]> readZip(InputStream inputStream) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        ZipInputStream zis = new ZipInputStream(inputStream);
        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int len;
            while ((len = zis.read(buffer)) > 0) {
                bos.write(buffer, 0, len);
            }
            entries.put(entry.getName(), bos.toByteArray());
            zis.closeEntry();
        }
        return entries;
    }

    private List<Map<String, Object>> buildCategoryManifest(List<KbCategoryModel> categories,
                                                            Map<Long, KbCategoryModel> categoryMap) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (KbCategoryModel cat : categories) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", cat.getId());
            item.put("parentId", cat.getParentId());
            item.put("name", cat.getName());
            item.put("path", buildCategoryPath(cat.getId(), categoryMap));
            list.add(item);
        }
        return list;
    }

    private Map<Long, KbCategoryModel> toCategoryMap(List<KbCategoryModel> categories) {
        Map<Long, KbCategoryModel> map = new HashMap<>();
        for (KbCategoryModel cat : categories) {
            map.put(cat.getId(), cat);
        }
        return map;
    }

    private String buildCategoryPath(Long categoryId, Map<Long, KbCategoryModel> categoryMap) {
        List<String> parts = new ArrayList<>();
        Long current = categoryId;
        while (current != null && current > 0) {
            KbCategoryModel cat = categoryMap.get(current);
            if (cat == null) {
                break;
            }
            parts.add(0, cat.getName());
            current = cat.getParentId();
        }
        return parts.isEmpty() ? "未分类" : String.join("/", parts);
    }

    private String sanitizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "untitled";
        }
        return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static class ImportItem {
        private final String title;
        private final String categoryPath;
        private final String filePath;
        private final String content;
        private final Integer status;

        ImportItem(String title, String categoryPath, String filePath, String content, Integer status) {
            this.title = title;
            this.categoryPath = categoryPath;
            this.filePath = filePath;
            this.content = content;
            this.status = status;
        }
    }
}
