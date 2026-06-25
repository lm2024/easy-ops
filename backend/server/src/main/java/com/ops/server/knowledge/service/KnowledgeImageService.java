package com.ops.server.knowledge.service;

import com.ops.common.exception.BusinessException;
import com.ops.common.model.KbImageModel;
import com.ops.server.mapper.KbImageMapper;
import com.ops.server.util.SecurityContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库图片上传服务
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class KnowledgeImageService {

    private static final int MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = new HashSet<String>(Arrays.asList(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"));

    @Autowired
    private KbImageMapper imageMapper;
    @Autowired
    private SecurityContext securityContext;

    @Value("${server.path:./data}")
    private String dataPath;

    /**
     * 上传文档图片
     */
    public Map<String, Object> upload(Long documentId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(1006, "文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(1006, "单图不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(1006, "仅支持 png/jpg/jpeg/gif/webp 图片");
        }

        String ext = contentType.contains("png") ? "png"
                : contentType.contains("gif") ? "gif"
                : contentType.contains("webp") ? "webp" : "jpg";
        String fileName = UUID.randomUUID().toString() + "." + ext;
        String dirPath = dataPath + "/kb/images/" + documentId;
        File dir = new File(dirPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String filePath = dirPath + "/" + fileName;

        KbImageModel image = new KbImageModel();
        image.setDocumentId(documentId);
        image.setFileName(file.getOriginalFilename());
        image.setFilePath(filePath);
        image.setFileSize((int) file.getSize());
        image.setMimeType(contentType);
        image.setUploaderId(securityContext.getCurrentUserId());
        image.setCreateTime(System.currentTimeMillis());
        imageMapper.insert(image);
        file.transferTo(new File(filePath));

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("imageId", image.getId());
        result.put("url", "/api/kb/images/" + image.getId());
        result.put("markdown", "![](" + result.get("url") + ")");
        return result;
    }

    /**
     * 获取图片文件
     */
    public File getImageFile(Long imageId) {
        KbImageModel image = imageMapper.findById(imageId);
        if (image == null) {
            return null;
        }
        return new File(image.getFilePath());
    }

    /**
     * 获取图片元信息
     */
    public KbImageModel findById(Long imageId) {
        return imageMapper.findById(imageId);
    }
}
