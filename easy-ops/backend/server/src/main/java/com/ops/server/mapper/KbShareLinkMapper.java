package com.ops.server.mapper;

import com.ops.common.model.KbShareLinkModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KbShareLinkMapper {
    List<KbShareLinkModel> selectAll();
    KbShareLinkModel selectById(@Param("id") Long id);
    int insert(KbShareLinkModel shareLink);
    int update(KbShareLinkModel shareLink);
    int delete(@Param("id") Long id);

    /** 按 token 查询分享链接 */
    KbShareLinkModel findByToken(@Param("token") String token);

    /** 查询文档的所有分享链接 */
    List<KbShareLinkModel> findByDocumentId(@Param("documentId") Long documentId);

    /** 删除文档的所有分享链接 */
    int deleteByDocumentId(@Param("documentId") Long documentId);
}
