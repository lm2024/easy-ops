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

    int deleteByNotificationIds(@Param("notificationIds") java.util.List<Long> notificationIds);
}
