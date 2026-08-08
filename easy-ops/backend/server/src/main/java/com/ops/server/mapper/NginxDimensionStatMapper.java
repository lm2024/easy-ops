package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NginxDimensionStatMapper {

    int batchInsertUa(@Param("rows") List<Map<String, Object>> rows);

    int batchInsertReferer(@Param("rows") List<Map<String, Object>> rows);

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
}
