package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper 接口
 * 职责：
 * - 提供用户数据的基础 CRUD；
 * - 管理端与账户接口通过 QueryWrapper 组合进行筛选（用户名/昵称/邮箱）与分页排序。
 *
 * 排序与筛选：
 * - 列表通常按 `id DESC` 或 `created_at DESC` 返回最近用户在前；
 * - 模糊匹配使用 `LIKE`，大小写敏感度由数据库配置决定。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}