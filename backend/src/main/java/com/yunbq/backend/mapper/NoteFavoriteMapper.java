package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NoteFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 便签收藏关系 Mapper 接口
 * 职责：
 * - 提供收藏计数与用户在一组便签中的收藏命中查询；
 * - 使用 MyBatis 动态 SQL 构建 IN 子句以支持批量集合。
 *
 * 边界与性能：
 * - 大集合建议分批；
 * - 返回结构与点赞统计保持一致，便于前端合并。
 */
@Mapper
public interface NoteFavoriteMapper extends BaseMapper<NoteFavorite> {

    @Select({
            "<script>",
            "SELECT note_id AS noteId, COUNT(*) AS cnt FROM note_favorites",
            " WHERE note_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            " GROUP BY note_id",
            "</script>"
    })
    /**
     * 统计一组便签的收藏数量。
     * 参数：
     * - ids：便签 ID 集合。
     * 返回：
     * - 列表项 { noteId, cnt }。
     */
    List<java.util.Map<String, Object>> countByNoteIds(@Param("ids") List<Long> ids);

    @Select({
            "<script>",
            "SELECT note_id FROM note_favorites",
            " WHERE user_id = #{userId} AND note_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"
    })
    /**
     * 查询指定用户在一组便签中的收藏命中（返回命中的便签ID）。
     * 参数：
     * - userId：用户 ID；
     * - ids：便签 ID 集合。
     * 返回：
     * - 用户已收藏的便签 ID 列表。
     */
    List<Long> findFavoritedNoteIdsByUser(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}