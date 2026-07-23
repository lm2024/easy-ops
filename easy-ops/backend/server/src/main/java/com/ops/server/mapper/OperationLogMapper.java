package com.ops.server.mapper;

import com.ops.common.model.OperationLogModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperationLogMapper {
    OperationLogModel findById(@Param("id") Long id);
    List<OperationLogModel> findByModule(@Param("module") String module, @Param("userId") Long userId,
                                         @Param("page") Integer page, @Param("pageSize") Integer pageSize);
    Long countByModule(@Param("module") String module, @Param("userId") Long userId);
    int insert(OperationLogModel log);

    int deleteBefore(@Param("cutoff") Long cutoff);
}
