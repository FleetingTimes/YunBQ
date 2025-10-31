package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.ErrorLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误日志 Mapper。
 */
@Mapper
public interface ErrorLogMapper extends BaseMapper<ErrorLog> {
}