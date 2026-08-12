package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * Nginx IP 维度统计 Mapper（独立于主表，支持 rank/ip 查询）
 */
@Mapper
public interface NginxIpStatMapper {

    int incrementIpStat(@Param("sourceId") Long sourceId, @Param("bucketTime") Long bucketTime,
                        @Param("clientIp") String clientIp, @Param("requestCount") int requestCount,
                        @Param("sumRequestTimeMs") long sumRequestTimeMs, @Param("maxRequestTimeMs") long maxRequestTimeMs,
                        @Param("slowCount") int slowCount, @Param("status5xx") int status5xx);

    int insertIpStat(@Param("row") Map<String, Object> row);

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

    int deleteBefore(@Param("cutoff") Long cutoff);
}
