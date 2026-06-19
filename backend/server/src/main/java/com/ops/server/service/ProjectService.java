package com.ops.server.service;

import com.ops.common.model.ProjectModel;
import com.ops.server.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class ProjectService {
    @Autowired
    private ProjectMapper projectMapper;

    public ProjectModel findById(Long id) { return projectMapper.findById(id); }
    public ProjectModel findByName(String name) { return projectMapper.findByName(name); }
    public List<ProjectModel> findByFilters(String status, Long nodeId, Integer page, Integer pageSize) {
        return projectMapper.findByFilters(status, nodeId, page, pageSize);
    }
    public Long countByFilters(String status, Long nodeId) { return projectMapper.countByFilters(status, nodeId); }
    public int insert(ProjectModel project) { return projectMapper.insert(project); }
    public int update(ProjectModel project) { return projectMapper.update(project); }
    public int deleteById(Long id) { return projectMapper.deleteById(id); }
}
