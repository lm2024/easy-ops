package com.ops.common.model;

import lombok.Data;
import java.io.Serializable;

/**
 * 知识库外链分享
 */
@Data
public class KbShareLinkModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long documentId;
    private String token;
    private String password;
    private Long expireTime;
    private Long createUserId;
    private Long createTime;
}
