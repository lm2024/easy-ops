package com.ops.server.mapper;

import com.ops.common.model.KbDocumentModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbDocumentMapper {
    KbDocumentModel findById(@Param("id") Long id);
    KbDocumentModel findByCategoryAndTitle(@Param("categoryId") Long categoryId,
                                           @Param("title") String title);
    List<KbDocumentModel> findByCategory(@Param("categoryId") Long categoryId,
                                         @Param("page") Integer page,
                                         @Param("pageSize") Integer pageSize);
    Long countByCategory(@Param("categoryId") Long categoryId);
    List<KbDocumentModel> search(@Param("keyword") String keyword,
                                 @Param("page") Integer page,
                                 @Param("pageSize") Integer pageSize);
    Long countSearch(@Param("keyword") String keyword);
    int insert(KbDocumentModel document);
    int update(KbDocumentModel document);
    int updateCategory(@Param("id") Long id, @Param("categoryId") Long categoryId, @Param("updateTime") Long updateTime);
    int incrementViewCount(@Param("id") Long id);
    int deleteById(@Param("id") Long id);

    /** 保存 Yjs 状态到 kb_document.yjs_state */
    int updateYjsState(@Param("id") Long id, @Param("yjsState") byte[] yjsState);

    /** 加载 Yjs 状态 */
    byte[] selectYjsState(@Param("id") Long id);

    /** 从 Yjs 导出的 Markdown 更新文档 content */
    int updateContentFromYjs(@Param("id") Long id, @Param("content") String content,
                             @Param("contentSize") Integer contentSize, @Param("updateTime") Long updateTime);
}
