package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口
 * 职责：
 * - 提供审计日志的增删改查基础能力；
 * - 管理后台使用 QueryWrapper + Page 进行分页与排序。
 *
 * 分页与排序：
 * - 管理端列表通常按 `created_at DESC` 返回最近记录在前；
 * - 过滤字段（如 level）由控制器构造条件后交由 Mapper 执行。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    // 这里保留扩展自定义查询的空间，例如复杂筛选或聚合统计。
}