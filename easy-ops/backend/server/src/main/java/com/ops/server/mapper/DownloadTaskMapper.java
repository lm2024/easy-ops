package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 下载任务记录表（文件管理 / 压缩下载，审计 + 兜底）。
 */
@Mapper
public interface DownloadTaskMapper {

    int insert(Map<String, Object> task);

    int updateStatus(@Param("taskId") String taskId, @Param("status") String status,
                     @Param("updateTime") long updateTime);

    Map<String, Object> findByTaskId(@Param("taskId") String taskId);

    List<Map<String, Object>> listRecent(@Param("limit") int limit);

    int deleteBefore(@Param("cutoff") long cutoff);

    int deleteByTaskId(@Param("taskId") String taskId);
}
