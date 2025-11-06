package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NoteLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/**
 * 便签点赞关系 Mapper 接口
 * 职责：
 * - 提供对便签点赞记录的统计与用户特定集合查询；
 * - 采用动态 SQL 处理大批量 ID 集合（IN 子句）。
 *
 * 边界与性能：
 * - 大量 ID 集合查询建议由服务层进行分批，避免 SQL 长度过长或索引失效；
 * - 计数聚合返回 {noteId, cnt} 结构，便于前端合并显示。
 */
@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {

    @Select({
        "<script>",
        "SELECT note_id AS noteId, COUNT(*) AS cnt FROM note_likes",
        " WHERE note_id IN ",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        " GROUP BY note_id",
        "</script>"
    })
    /**
     * 统计一组便签的点赞数量。
     * 参数：
     * - ids：便签 ID 集合（非空）。
     * 返回：
     * - 列表项 { noteId, cnt }。
     */
    List<Map<String, Object>> countByNoteIds(@Param("ids") List<Long> ids);

    @Select({
        "<script>",
        "SELECT note_id FROM note_likes",
        " WHERE user_id = #{userId} AND note_id IN ",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"
    })
    /**
     * 查询指定用户在一组便签中的点赞记录（返回命中的便签ID）。
     * 参数：
     * - userId：用户 ID；
     * - ids：便签 ID 集合。
     * 返回：
     * - 用户已点赞的便签 ID 列表。
     */
    List<Long> findLikedNoteIdsByUser(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}