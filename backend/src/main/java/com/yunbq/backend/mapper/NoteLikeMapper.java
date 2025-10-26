package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.NoteLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

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
    List<Map<String, Object>> countByNoteIds(@Param("ids") List<Long> ids);

    @Select({
        "<script>",
        "SELECT note_id FROM note_likes",
        " WHERE user_id = #{userId} AND note_id IN ",
        "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
        "</script>"
    })
    List<Long> findLikedNoteIdsByUser(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}