package com.ops.server.traffic.service;

import com.ops.common.model.NginxSourceWhitelistModel;
import com.ops.server.mapper.NginxSourceWhitelistMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志源白名单：配置管理（CRUD）。白名单在查询侧生效，不参与统计/告警。
 */
@Service
public class NginxSourceWhitelistService {

    @Autowired
    private NginxSourceWhitelistMapper whitelistMapper;

    public List<NginxSourceWhitelistModel> listBySource(Long sourceId) {
        if (sourceId == null) {
            return new ArrayList<NginxSourceWhitelistModel>();
        }
        return whitelistMapper.findBySourceId(sourceId);
    }

    /**
     * 全量保存：以传入列表为准——新增/更新其中项，删除库中存在但本次未传的项。
     */
    public List<NginxSourceWhitelistModel> saveAll(Long sourceId, Long tenantId, List<NginxSourceWhitelistModel> items) {
        if (sourceId == null) {
            return new ArrayList<NginxSourceWhitelistModel>();
        }
        long now = System.currentTimeMillis();
        List<Long> keptIds = new ArrayList<Long>();
        if (items != null) {
            for (NginxSourceWhitelistModel m : items) {
                m.setSourceId(sourceId);
                m.setTenantId(tenantId);
                m.setUpdateTime(now);
                if (m.getId() == null) {
                    m.setCreateTime(now);
                    whitelistMapper.insert(m);
                } else {
                    whitelistMapper.update(m);
                }
                if (m.getId() != null) {
                    keptIds.add(m.getId());
                }
            }
        }
        whitelistMapper.deleteByIds(sourceId, tenantId, keptIds);
        return listBySource(sourceId);
    }

    public void deleteBySource(Long sourceId, Long tenantId) {
        if (sourceId != null) {
            whitelistMapper.deleteBySourceId(sourceId, tenantId);
        }
    }
}
