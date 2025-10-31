package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志 Mapper 接口。
 * 说明：
 * - 继承 MyBatis-Plus 的 BaseMapper，直接具备增删改查的基础能力。
 * - 当前项目中 AdminController 已依赖该接口进行分页查询，因此需要补齐定义。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    // 这里保留扩展自定义查询的空间，例如复杂筛选或聚合统计。
}