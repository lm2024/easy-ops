package com.ops.common.model;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户通知状态模型
 */
@Data
public class UserNotificationStateModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long notificationId;
    private Long userId;
    private Integer readStatus;
    private Integer ackStatus;
    private Long ackTime;
}
