package com.ops.server.service;

import com.ops.common.model.VersionModel;
import com.ops.server.mapper.VersionPackageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VersionService {
    @Autowired
    private VersionPackageMapper versionPackageMapper;

    public VersionModel findById(Long id) { return versionPackageMapper.findById(id); }
    public String getProjectNameByProjectId(Long projectId) { return versionPackageMapper.getProjectNameByProjectId(projectId); }
    public List<VersionModel> findByProjectId(Long projectId, Integer page, Integer pageSize) {
        return versionPackageMapper.findByProjectId(projectId, page, pageSize);
    }
    public Long countByProjectId(Long projectId) { return versionPackageMapper.countByProjectId(projectId); }
    public int insert(VersionModel version) { return versionPackageMapper.insert(version); }
    public int deleteById(Long id) { return versionPackageMapper.deleteById(id); }
}
