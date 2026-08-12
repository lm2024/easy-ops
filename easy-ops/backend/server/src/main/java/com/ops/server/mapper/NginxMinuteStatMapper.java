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

    /** 分批删除：每次最多删 batchSize 条，返回实际删除数 */
    int deleteBeforeBucketTimeBatch(@Param("cutoff") Long cutoff, @Param("batchSize") int batchSize);

    List<Map<String, Object>> sumByIp(@Param("sourceIds") List<Long> sourceIds,
                                      @Param("startTime") Long startTime,
                                      @Param("endTime") Long endTime,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("pageSize") int pageSize,
                                      @Param("sort") String sort,
                                      @Param("wl") Map<String, Object> wl);

    int countByIp(@Param("sourceIds") List<Long> sourceIds,
                  @Param("startTime") Long startTime,
                  @Param("endTime") Long endTime,
                  @Param("keyword") String keyword,
                  @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> sumByUri(@Param("sourceIds") List<Long> sourceIds,
                                       @Param("startTime") Long startTime,
                                       @Param("endTime") Long endTime,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("pageSize") int pageSize,
                                       @Param("sort") String sort,
                                       @Param("wl") Map<String, Object> wl);

    int countByUri(@Param("sourceIds") List<Long> sourceIds,
                   @Param("startTime") Long startTime,
                   @Param("endTime") Long endTime,
                   @Param("keyword") String keyword,
                   @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> sumByIpUri(@Param("sourceIds") List<Long> sourceIds,
                                         @Param("startTime") Long startTime,
                                         @Param("endTime") Long endTime,
                                         @Param("clientIp") String clientIp,
                                         @Param("uri") String uri,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize,
                                         @Param("sort") String sort,
                                         @Param("wl") Map<String, Object> wl);

    int countByIpUri(@Param("sourceIds") List<Long> sourceIds,
                     @Param("startTime") Long startTime,
                     @Param("endTime") Long endTime,
                     @Param("clientIp") String clientIp,
                     @Param("uri") String uri,
                     @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> sumSlowByUri(@Param("sourceIds") List<Long> sourceIds,
                                           @Param("startTime") Long startTime,
                                           @Param("endTime") Long endTime,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize,
                                           @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> sumByMethod(@Param("sourceIds") List<Long> sourceIds,
                                          @Param("startTime") Long startTime,
                                          @Param("endTime") Long endTime,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize,
                                          @Param("sort") String sort,
                                          @Param("wl") Map<String, Object> wl);

    int countByMethod(@Param("sourceIds") List<Long> sourceIds,
                      @Param("startTime") Long startTime,
                      @Param("endTime") Long endTime,
                      @Param("wl") Map<String, Object> wl);

    int countSlowByUri(@Param("sourceIds") List<Long> sourceIds,
                       @Param("startTime") Long startTime,
                       @Param("endTime") Long endTime,
                       @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> trendByMinute(@Param("sourceIds") List<Long> sourceIds,
                                            @Param("startTime") Long startTime,
                                            @Param("endTime") Long endTime,
                                            @Param("wl") Map<String, Object> wl);

    Map<String, Object> overview(@Param("sourceIds") List<Long> sourceIds,
                                 @Param("startTime") Long startTime,
                                 @Param("endTime") Long endTime,
                                 @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> listIpAboveThreshold(@Param("sourceId") Long sourceId,
                                                     @Param("startTime") Long startTime,
                                                     @Param("endTime") Long endTime,
                                                     @Param("threshold") long threshold,
                                                     @Param("limit") int limit,
                                                     @Param("wl") Map<String, Object> wl);

    List<Map<String, Object>> listUriAboveThreshold(@Param("sourceId") Long sourceId,
                                                      @Param("startTime") Long startTime,
                                                      @Param("endTime") Long endTime,
                                                      @Param("threshold") long threshold,
                                                      @Param("limit") int limit,
                                                      @Param("wl") Map<String, Object> wl);

    Map<String, Object> sumStatusAndSlow(@Param("sourceId") Long sourceId,
                                         @Param("startTime") Long startTime,
                                         @Param("endTime") Long endTime,
                                         @Param("wl") Map<String, Object> wl);
}
