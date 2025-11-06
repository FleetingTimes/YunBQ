package com.yunbq.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yunbq.backend.model.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 消息中心 Mapper 接口
 * 职责：
 * - 提供消息的分页查询、分组统计（未读计数）、总量计算等；
 * - 联表查询触发者（用户）与便签摘要信息用于前端展示。
 *
 * 分页与筛选：
 * - 列表查询支持按消息类型可选过滤（type 允许为 null 表示不限）；
 * - 排序统一按 `created_at DESC` 返回最新消息在前；
 * - LIMIT 使用偏移量与大小，入参需由服务层计算 `offset = (page-1)*size` 并控制 size 上限。
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    /**
     * 未读计数（按类型分组）。
     * 参数：
     * - uid：接收者用户 ID。
     * 返回：
     * - 列表项包含 { type, cnt }，用于构造前端类型分组计数。
     */
    @Select({
            "SELECT type, COUNT(*) AS cnt FROM messages",
            " WHERE receiver_user_id = #{uid} AND is_read = 0",
            " GROUP BY type"
    })
    List<Map<String,Object>> unreadCountsByType(@Param("uid") Long uid);

    /**
     * 总未读是否存在。
     * 参数：
     * - uid：接收者用户 ID。
     * 返回：
     * - 未读总数（用于 hasNew 判断）。
     */
    @Select("SELECT COUNT(*) FROM messages WHERE receiver_user_id = #{uid} AND is_read = 0")
    Long unreadTotal(@Param("uid") Long uid);

    /**
     * 分页查询消息，并联表取触发者信息与拾言摘要。
     * 筛选与排序：
     * - 可选按类型过滤；按 `m.created_at DESC` 排序；
     * 分页：
     * - 使用 `LIMIT #{offset}, #{size}`，要求服务层计算偏移并控制 size 的合理上限（如 ≤ 50）。
     * 参数：
     * - uid：接收者用户 ID；
     * - type：消息类型（可为 null 表示不限）；
     * - offset/size：分页偏移与大小。
     * 返回：
     * - 扁平字段的 Map 列表，包含触发者昵称与摘要文本，便于前端直接渲染。
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

    /**
     * 总数用于分页。
     * 参数：
     * - uid：接收者用户 ID；
     * - type：消息类型过滤（可为 null）。
     * 返回：
     * - 满足条件的总记录数。
     */
    @Select({
            "SELECT COUNT(*) FROM messages",
            " WHERE receiver_user_id = #{uid}",
            "   AND (#{type} IS NULL OR type = #{type})"
    })
    Long totalByType(@Param("uid") Long uid, @Param("type") String type);
}