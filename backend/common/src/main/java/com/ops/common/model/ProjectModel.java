package com.ops.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.io.Serializable;

/**
 * 项目管理模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectModel implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String nodeIds;
    private String startScript;
    private String stopScript;
    private String restartScript;
    private String jvmOpts;
    private String envVars;
    private String jarName;
    private String deployDir;
    private Integer status;
    private Long createTime;
    private Long updateTime;
}
