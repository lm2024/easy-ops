package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface FileAccessLogMapper {
    int insert(Map<String, Object> log);

    int deleteBefore(@Param("cutoff") Long cutoff);
}
