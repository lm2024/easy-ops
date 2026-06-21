package com.ops.server.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysConfigMapper {

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} LIMIT 1")
    String getValue(@Param("key") String key);

    @Update("MERGE INTO sys_config (config_key, config_value, description, update_time) KEY (config_key) " +
            "VALUES (#{key}, #{value}, #{desc}, #{time})")
    void upsert(@Param("key") String key, @Param("value") String value, @Param("desc") String desc, @Param("time") long time);
}
