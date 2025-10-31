package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.RequestLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请求日志 Mapper，用于持久化 RequestLog 记录。
 */
@Mapper
public interface RequestLogMapper extends BaseMapper<RequestLog> {
}