package com.ops.server.mapper;

import com.ops.common.model.NotificationRecordModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 站内通知 Mapper
 */
@Mapper
public interface NotificationRecordMapper {

    NotificationRecordModel findById(@Param("id") Long id);

    List<NotificationRecordModel> findByUserId(@Param("userId") Long userId,
                                               @Param("now") Long now,
                                               @Param("page") Integer page,
                                               @Param("pageSize") Integer pageSize);

    Long countByUserId(@Param("userId") Long userId, @Param("now") Long now);

    List<NotificationRecordModel> findUnackedAlerts(@Param("userId") Long userId,
                                                    @Param("now") Long now);

    int insert(NotificationRecordModel record);

    int deleteExpired(@Param("now") Long now);
}
