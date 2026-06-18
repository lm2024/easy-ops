package com.ops.server.mapper;

import com.ops.common.model.UserModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    UserModel findById(@Param("id") Long id);
    UserModel findByUsername(@Param("username") String username);
    List<UserModel> findAll(@Param("page") Integer page, @Param("pageSize") Integer pageSize);
    Long countAll();
    int insert(UserModel user);
    int update(UserModel user);
    int deleteById(@Param("id") Long id);
}
