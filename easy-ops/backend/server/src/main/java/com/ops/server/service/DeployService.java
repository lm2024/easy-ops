package com.ops.server.service;

import com.ops.common.model.DeployModel;
import com.ops.server.mapper.DeployRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class DeployService {
    @Autowired
    private DeployRecordMapper deployRecordMapper;

    public DeployModel findById(Long id) { return deployRecordMapper.findById(id); }
    public List<DeployModel> findByProjectId(Long projectId, Integer page, Integer pageSize) {
        return deployRecordMapper.findByProjectId(projectId, page, pageSize);
    }
    public Long countByProjectId(Long projectId) { return deployRecordMapper.countByProjectId(projectId); }
    public int insert(DeployModel deploy) { return deployRecordMapper.insert(deploy); }
}
