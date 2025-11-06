package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.RequestLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 请求日志 Mapper 接口
 * 职责：
 * - 提供请求日志记录的基础 CRUD；
 * - 管理端支持按 URI、状态码、请求ID 的过滤与分页。
 *
 * 分页与排序：
 * - 列表通常按 `created_at DESC` 排序；
 * - 过滤条件由控制器使用 QueryWrapper 组合后传递。
 */
@Mapper
public interface RequestLogMapper extends BaseMapper<RequestLog> {
}