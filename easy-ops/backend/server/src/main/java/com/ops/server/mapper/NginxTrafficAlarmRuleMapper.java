package com.ops.server.mapper;

import com.ops.common.model.NginxTrafficAlarmRuleModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NginxTrafficAlarmRuleMapper {

    List<NginxTrafficAlarmRuleModel> findBySourceId(@Param("sourceId") Long sourceId);

    List<NginxTrafficAlarmRuleModel> findAllEnabled();

    NginxTrafficAlarmRuleModel findBySourceIdAndType(@Param("sourceId") Long sourceId,
                                                     @Param("ruleType") String ruleType);

    int insert(NginxTrafficAlarmRuleModel model);

    int update(NginxTrafficAlarmRuleModel model);

    int deleteBySourceId(@Param("sourceId") Long sourceId);
}
