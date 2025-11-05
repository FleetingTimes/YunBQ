package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 未读计数（按类型分组）。
     */
    @Select({
            "SELECT type, COUNT(*) AS cnt FROM messages",
            " WHERE receiver_user_id = #{uid} AND is_read = 0",
            " GROUP BY type"
    })
    List<Map<String,Object>> unreadCountsByType(@Param("uid") Long uid);

    /**
     * 总未读是否存在。
     */
    @Select("SELECT COUNT(*) FROM messages WHERE receiver_user_id = #{uid} AND is_read = 0")
    Long unreadTotal(@Param("uid") Long uid);

    /**
     * 分页查询消息，并联表取触发者信息与拾言摘要。
     * 注意：MyBatis 注解 SQL 中 LIMIT 的占位符写法需显式传递 offset + size。
     */
    @Select({
            "SELECT m.id, m.type, m.is_read AS isRead, m.created_at AS createdAt,",
            "       u.username AS actorUsername, u.nickname AS actorNickname, u.avatar_url AS actorAvatarUrl,",
            "       n.id AS noteId, SUBSTRING(n.content, 1, 160) AS contentSnippet,",
            "       m.message",
            "  FROM messages m",
            "  LEFT JOIN users u ON u.id = m.actor_user_id",
            "  LEFT JOIN shiyan n ON n.id = m.note_id",
            " WHERE m.receiver_user_id = #{uid}",
            "   AND (#{type} IS NULL OR m.type = #{type})",
            " ORDER BY m.created_at DESC",
            " LIMIT #{offset}, #{size}"
    })
    List<Map<String,Object>> listWithDetails(@Param("uid") Long uid,
                                             @Param("type") String type,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    /** 总数用于分页 */
    @Select({
            "SELECT COUNT(*) FROM messages",
            " WHERE receiver_user_id = #{uid}",
            "   AND (#{type} IS NULL OR type = #{type})"
    })
    Long totalByType(@Param("uid") Long uid, @Param("type") String type);
}