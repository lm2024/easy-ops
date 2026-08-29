package com.ops.server.mapper;

import com.ops.common.model.ArthasDiagnoseRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ArthasDiagnoseRecordMapper {
    int insert(ArthasDiagnoseRecordModel record);
    int update(ArthasDiagnoseRecordModel record);
    ArthasDiagnoseRecordModel findById(@Param("id") Long id);
    ArthasDiagnoseRecordModel findBySessionId(@Param("sessionId") String sessionId);
    /**
     * 按项目分页查询诊断记录，支持按节点/状态/起始时间筛选。
     * 筛选参数传 null 表示不限制。
     */
    List<ArthasDiagnoseRecordModel> findByProjectId(@Param("projectId") Long projectId,
                                                       @Param("nodeId") Long nodeId,
                                                       @Param("status") String status,
                                                       @Param("startTime") Long startTime,
                                                       @Param("endTime") Long endTime,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);
    int countByProjectId(@Param("projectId") Long projectId,
                         @Param("nodeId") Long nodeId,
                         @Param("status") String status,
                         @Param("startTime") Long startTime,
                         @Param("endTime") Long endTime);
    List<Long> findExpiredIds(@Param("cutoff") Long cutoff);
    int deleteByIds(@Param("ids") List<Long> ids);

    /**
     * 仅刷新最后活跃时间。
     * 用独立语句代替全字段 update，避免并发命令执行时互相覆盖整行数据。
     */
    int touchUpdatedAt(@Param("id") Long id, @Param("updatedAt") Long updatedAt);

    /**
     * 统计同一目标进程上"其他"仍在使用中的会话数。
     *
     * Agent 侧的 Arthas 进程是按 pid 持有的（一个目标进程只有一个物理会话），
     * 而这里的诊断记录是按 sessionId 持有的（逻辑会话），多个逻辑会话共享同一个物理会话。
     * stop 之前必须先问一句：还有没有别人在用？否则会误停他人的诊断。
     *
     * @param excludeSessionId 排除自身
     */
    int countActivePeers(@Param("nodeId") Long nodeId,
                         @Param("pid") Integer pid,
                         @Param("excludeSessionId") String excludeSessionId);

    /**
     * 把状态为 ATTACHING/RUNNING 但已经太久没动静的记录置为 FINISHED。
     * 防止异常退出（浏览器直接关掉、进程被杀）留下的僵尸记录一直占用会话列表。
     */
    int finishStale(@Param("cutoff") Long cutoff, @Param("statuses") List<String> statuses);
}
