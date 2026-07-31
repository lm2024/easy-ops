package com.ops.server.mapper;

import com.ops.common.model.NginxMinuteStatModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NginxMinuteStatMapper {

    int incrementStat(NginxMinuteStatModel row);

    int insertStat(NginxMinuteStatModel row);

    int deleteBeforeBucketTime(@Param("cutoff") Long cutoff);

    List<Map<String, Object>> sumByIp(@Param("sourceIds") List<Long> sourceIds,
                                      @Param("startTime") Long startTime,
                                      @Param("endTime") Long endTime,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("pageSize") int pageSize);

    int countByIp(@Param("sourceIds") List<Long> sourceIds,
                  @Param("startTime") Long startTime,
                  @Param("endTime") Long endTime,
                  @Param("keyword") String keyword);

    List<Map<String, Object>> sumByUri(@Param("sourceIds") List<Long> sourceIds,
                                       @Param("startTime") Long startTime,
                                       @Param("endTime") Long endTime,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize);

    int countByUri(@Param("sourceIds") List<Long> sourceIds,
                   @Param("startTime") Long startTime,
                   @Param("endTime") Long endTime,
                   @Param("keyword") String keyword);

    List<Map<String, Object>> sumByIpUri(@Param("sourceIds") List<Long> sourceIds,
                                         @Param("startTime") Long startTime,
                                         @Param("endTime") Long endTime,
                                         @Param("clientIp") String clientIp,
                                         @Param("uri") String uri,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    int countByIpUri(@Param("sourceIds") List<Long> sourceIds,
                     @Param("startTime") Long startTime,
                     @Param("endTime") Long endTime,
                     @Param("clientIp") String clientIp,
                     @Param("uri") String uri);

    List<Map<String, Object>> sumSlowByUri(@Param("sourceIds") List<Long> sourceIds,
                                           @Param("startTime") Long startTime,
                                           @Param("endTime") Long endTime,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    int countSlowByUri(@Param("sourceIds") List<Long> sourceIds,
                       @Param("startTime") Long startTime,
                       @Param("endTime") Long endTime);

    List<Map<String, Object>> trendByMinute(@Param("sourceIds") List<Long> sourceIds,
                                            @Param("startTime") Long startTime,
                                            @Param("endTime") Long endTime);

    Map<String, Object> overview(@Param("sourceIds") List<Long> sourceIds,
                                 @Param("startTime") Long startTime,
                                 @Param("endTime") Long endTime);

    List<Map<String, Object>> listIpAboveThreshold(@Param("sourceId") Long sourceId,
                                                     @Param("startTime") Long startTime,
                                                     @Param("endTime") Long endTime,
                                                     @Param("threshold") long threshold,
                                                     @Param("limit") int limit);

    List<Map<String, Object>> listUriAboveThreshold(@Param("sourceId") Long sourceId,
                                                      @Param("startTime") Long startTime,
                                                      @Param("endTime") Long endTime,
                                                      @Param("threshold") long threshold,
                                                      @Param("limit") int limit);

    Map<String, Object> sumStatusAndSlow(@Param("sourceId") Long sourceId,
                                         @Param("startTime") Long startTime,
                                         @Param("endTime") Long endTime);
}
