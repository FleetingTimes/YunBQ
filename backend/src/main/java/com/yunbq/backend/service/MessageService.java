package com.yunbq.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.mapper.MessageMapper;
import com.yunbq.backend.mapper.NoteMapper;
import com.yunbq.backend.mapper.UserMapper;
import com.yunbq.backend.model.Message;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息服务：
 * - 创建行为消息（点赞/收藏）供作者接收；
 * - 列表分页；
 * - 标记已读/删除；
 * - 未读计数与是否存在新消息。
 */
@Service
public class MessageService {
    private final MessageMapper messageMapper;
    private final NoteMapper noteMapper;
    private final UserMapper userMapper;

    public MessageService(MessageMapper messageMapper, NoteMapper noteMapper, UserMapper userMapper) {
        this.messageMapper = messageMapper;
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
    }

    /** 创建点赞消息（userId 点赞了 noteId，对作者产生消息） */
    public void createLikeMessage(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) return;
        Long receiver = n.getUserId();
        if (receiver == null || receiver.equals(userId)) return; // 自己点赞自己的拾言不发消息
        User actor = userMapper.selectById(userId);
        Message m = new Message();
        m.setType("like");
        m.setActorUserId(userId);
        m.setReceiverUserId(receiver);
        m.setNoteId(noteId);
        m.setMessage(null); // 文案前端渲染，也可在此写固定文案
        m.setIsRead(false);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /** 创建收藏消息（userId 收藏了 noteId，对作者产生消息） */
    public void createFavoriteMessage(Long userId, Long noteId) {
        Note n = noteMapper.selectById(noteId);
        if (n == null) return;
        Long receiver = n.getUserId();
        if (receiver == null || receiver.equals(userId)) return; // 自己收藏自己的拾言不发消息
        User actor = userMapper.selectById(userId);
        Message m = new Message();
        m.setType("favorite");
        m.setActorUserId(userId);
        m.setReceiverUserId(receiver);
        m.setNoteId(noteId);
        m.setMessage(null);
        m.setIsRead(false);
        m.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(m);
    }

    /** 消息分页列表（带联表详情），与前端 Messages.vue 参数保持一致 */
    public Map<String, Object> list(Long uid, int page, int size, String type) {
        int offset = Math.max(0, (page - 1) * size);
        List<Map<String, Object>> items = messageMapper.listWithDetails(uid, type, offset, size);
        Long total = messageMapper.totalByType(uid, type);
        Map<String, Object> resp = new HashMap<>();
        resp.put("items", items);
        resp.put("total", total);
        return resp;
    }

    /** 标记已读 */
    public boolean markRead(Long uid, Long id) {
        Message m = messageMapper.selectById(id);
        if (m == null || !uid.equals(m.getReceiverUserId())) return false;
        messageMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Message>()
                .eq("id", id).set("is_read", true));
        return true;
    }

    /** 删除消息 */
    public boolean delete(Long uid, Long id) {
        Message m = messageMapper.selectById(id);
        if (m == null || !uid.equals(m.getReceiverUserId())) return false;
        return messageMapper.delete(new QueryWrapper<Message>().eq("id", id)) > 0;
    }

    /** 未读计数（含是否有新消息） */
    public Map<String, Object> unreadCounts(Long uid) {
        List<Map<String, Object>> rows = messageMapper.unreadCountsByType(uid);
        Map<String, Long> counts = new HashMap<>();
        for (Map<String, Object> r : rows) {
            String t = String.valueOf(r.get("type"));
            Long c = ((Number) r.get("cnt")).longValue();
            counts.put(t, c);
        }
        Long total = messageMapper.unreadTotal(uid);
        Map<String, Object> resp = new HashMap<>();
        resp.put("counts", counts);
        resp.put("total", total);
        resp.put("hasNew", total != null && total > 0);
        return resp;
    }
}