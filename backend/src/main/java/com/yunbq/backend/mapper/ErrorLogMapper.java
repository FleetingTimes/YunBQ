package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.ErrorLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错误日志 Mapper 接口
 * 职责：
 * - 提供后端运行时异常日志的基础 CRUD；
 * - 管理端支持按异常类型与请求ID过滤并分页导出。
 *
 * 排序：
 * - 列表通常按 `created_at DESC` 返回最近错误在前。
 */
@Mapper
public interface ErrorLogMapper extends BaseMapper<ErrorLog> {
}