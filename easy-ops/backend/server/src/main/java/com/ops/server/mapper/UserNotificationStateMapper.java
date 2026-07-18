package com.ops.server.mapper;

import com.ops.common.model.UserNotificationStateModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户通知状态 Mapper
 */
@Mapper
public interface UserNotificationStateMapper {

    UserNotificationStateModel findByNotificationAndUser(@Param("notificationId") Long notificationId,
                                                         @Param("userId") Long userId);

    int insert(UserNotificationStateModel state);

    int updateRead(@Param("notificationId") Long notificationId,
                   @Param("userId") Long userId,
                   @Param("readStatus") Integer readStatus);

    int updateAck(@Param("notificationId") Long notificationId,
                  @Param("userId") Long userId,
                  @Param("ackStatus") Integer ackStatus,
                  @Param("ackTime") Long ackTime);

    Long countUnread(@Param("userId") Long userId, @Param("now") Long now);

    /** 全部标记已读（插入或更新所有未读通知的状态） */
    int markAllRead(@Param("userId") Long userId);

    /** 清空已读通知（将已读通知设为过期） */
    int clearRead(@Param("userId") Long userId, @Param("now") Long now);

    int deleteByNotificationIds(@Param("notificationIds") java.util.List<Long> notificationIds);
}
