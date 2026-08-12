package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NginxDimensionStatMapper {

    int batchInsertUa(@Param("rows") List<Map<String, Object>> rows);

    int incrementUaStat(@Param("sourceId") Long sourceId, @Param("bucketTime") Long bucketTime,
                        @Param("userAgent") String userAgent, @Param("requestCount") int requestCount,
                        @Param("sumRequestTimeMs") long sumRequestTimeMs, @Param("maxRequestTimeMs") long maxRequestTimeMs,
                        @Param("slowCount") int slowCount, @Param("status5xx") int status5xx);

    int insertUaStat(@Param("row") Map<String, Object> row);

    int batchInsertReferer(@Param("rows") List<Map<String, Object>> rows);

    int incrementRefererStat(@Param("sourceId") Long sourceId, @Param("bucketTime") Long bucketTime,
                             @Param("referer") String referer, @Param("requestCount") int requestCount,
                             @Param("sumRequestTimeMs") long sumRequestTimeMs, @Param("maxRequestTimeMs") long maxRequestTimeMs,
                             @Param("slowCount") int slowCount, @Param("status5xx") int status5xx);

    int insertRefererStat(@Param("row") Map<String, Object> row);

    int batchInsertSample(@Param("rows") List<Map<String, Object>> rows);

    List<Map<String, Object>> sumByUa(@Param("sourceIds") List<Long> sourceIds,
                                      @Param("startTime") Long startTime,
                                      @Param("endTime") Long endTime,
                                      @Param("keyword") String keyword,
                                      @Param("offset") int offset,
                                      @Param("pageSize") int pageSize);

    int countByUa(@Param("sourceIds") List<Long> sourceIds,
                  @Param("startTime") Long startTime,
                  @Param("endTime") Long endTime,
                  @Param("keyword") String keyword);

    List<Map<String, Object>> sumByReferer(@Param("sourceIds") List<Long> sourceIds,
                                           @Param("startTime") Long startTime,
                                           @Param("endTime") Long endTime,
                                           @Param("keyword") String keyword,
                                           @Param("offset") int offset,
                                           @Param("pageSize") int pageSize);

    int countByReferer(@Param("sourceIds") List<Long> sourceIds,
                       @Param("startTime") Long startTime,
                       @Param("endTime") Long endTime,
                       @Param("keyword") String keyword);

    List<Map<String, Object>> listSamples(@Param("sourceIds") List<Long> sourceIds,
                                          @Param("startTime") Long startTime,
                                          @Param("endTime") Long endTime,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);

    int deleteSamplesBefore(@Param("cutoff") Long cutoff);

    int deleteUaBefore(@Param("cutoff") Long cutoff);

    int deleteRefererBefore(@Param("cutoff") Long cutoff);
}
