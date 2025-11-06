package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.AuthLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证日志 Mapper 接口
 * 职责：
 * - 提供认证事件日志的基础 CRUD；
 * - 管理端支持按用户名与是否成功（success）过滤并分页导出。
 *
 * 排序：
 * - 列表通常按 `created_at DESC` 返回最近认证事件在前。
 */
@Mapper
public interface AuthLogMapper extends BaseMapper<AuthLog> {
}