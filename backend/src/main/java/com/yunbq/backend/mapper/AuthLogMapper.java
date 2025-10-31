package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.AuthLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证日志 Mapper。
 */
@Mapper
public interface AuthLogMapper extends BaseMapper<AuthLog> {
}