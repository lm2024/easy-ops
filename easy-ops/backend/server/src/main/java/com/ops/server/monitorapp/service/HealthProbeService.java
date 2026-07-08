package com.ops.server.monitorapp.service;

import com.ops.common.model.ProjectHealthProbeModel;
import com.ops.server.mapper.ProjectHealthProbeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目 HTTP 健康探针配置 CRUD
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class HealthProbeService {

    @Autowired
    private ProjectHealthProbeMapper probeMapper;

    /**
     * 获取项目探针配置
     */
    public ProjectHealthProbeModel getByProjectId(Long projectId) {
        return probeMapper.findByProjectId(projectId);
    }

    /**
     * 保存或更新探针配置
     */
    public ProjectHealthProbeModel save(ProjectHealthProbeModel probe) {
        long now = System.currentTimeMillis();
        ProjectHealthProbeModel existing = probeMapper.findByProjectId(probe.getProjectId());
        if (existing == null) {
            if (probe.getEnabled() == null) {
                probe.setEnabled(1);
            }
            if (probe.getMethod() == null) {
                probe.setMethod("GET");
            }
            if (probe.getExpectedStatus() == null) {
                probe.setExpectedStatus(200);
            }
            if (probe.getTimeoutMs() == null) {
                probe.setTimeoutMs(3000);
            }
            probe.setCreateTime(now);
            probe.setUpdateTime(now);
            probeMapper.insert(probe);
        } else {
            probe.setId(existing.getId());
            probe.setCreateTime(existing.getCreateTime());
            probe.setUpdateTime(now);
            probeMapper.update(probe);
        }
        return probeMapper.findByProjectId(probe.getProjectId());
    }
}
