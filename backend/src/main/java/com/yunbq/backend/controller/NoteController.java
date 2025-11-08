package com.yunbq.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yunbq.backend.dto.NoteRequest;
import com.yunbq.backend.dto.NoteItem;
import com.yunbq.backend.dto.PageResult;
import com.yunbq.backend.dto.ImportNotesRequest;
import com.yunbq.backend.model.Note;
import com.yunbq.backend.service.NoteService;
import com.yunbq.backend.util.AuthUtil;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
// 路径别名说明：
// - 为了与前端“拾言”品牌统一，新增类级别路径别名 /api/shiyan；
// - 保持与历史路径 /api/notes 等价，避免前端旧版本或第三方脚本立即失效；
// - 所有方法映射均继承该别名数组，因此不需要逐个方法重复添加别名。
@RequestMapping({"/api/notes", "/api/shiyan"})
/**
 * 拾言（便签）接口控制器
 * 职责：
 * - 提供拾言的查询、创建、编辑、删除等业务端点（包括公开与个人视图）；
 * - 点赞/取消点赞、收藏/取消收藏等交互端点；
 * - 返回结构兼容前端字段命名差异（如 likeCount/like_count, favoriteCount/favorite_count）。
 * 安全：
 * - 部分查询接口允许匿名访问（公开内容）；
 * - 交互与个人数据相关端点需要登录，使用 JwtAuthenticationFilter 写入 SecurityContext；
 * - 通过 AuthUtil.currentUserId() 获取当前用户ID，空值表示未登录。
 * 日志：
 * - 重要操作路径记录审计日志（如点赞/收藏），便于后续分析与导出。
 */
public class NoteController {

    private final NoteService noteService;
    // 说明：加入日志记录用于观测控制层是否成功接收到请求以及关键参数（方法、路径、用户ID、目标ID等）。
    // 这有助于前后端联调时快速定位是“未到达控制器”还是“控制器执行过程中出现业务错误”。
    private static final Logger log = LoggerFactory.getLogger(NoteController.class);

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    /**
     * 便签列表（分页）。
     *
     * 过滤参数：
     * - {@code q}：全文检索关键字（内容/标签等）；
     * - {@code archived}：是否仅返回归档便签（null 表示不限）；
     * - {@code isPublic}：是否仅返回公开便签（null 表示不限）；
     * - {@code mineOnly}：是否仅返回当前用户的便签（true 需登录）。
     *
     * 分页参数：
     * - {@code page}：页码，默认 1；
     * - {@code size}：每页条数，默认 10。
     *
     * 安全与行为：
     * - 登录态通过 {@code AuthUtil.currentUserId()} 识别；
     * - 未登录时仅返回公开内容，且用户态标记（我是否点赞/收藏）均为 false；
     * - 服务层负责精确的过滤逻辑与范围控制（例如 size 的上限）。
     *
     * @return 200 OK，{@code PageResult<NoteItem>}，包含 items/total/page/size
     */
    public ResponseEntity<PageResult<NoteItem>> list(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(required = false) Boolean archived,
                                           @RequestParam(required = false) Boolean isPublic,
                                           @RequestParam(required = false) Boolean mineOnly) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：记录列表查询的入参及当前用户，以便确认请求是否达到控制器。
        log.info("[NoteController] GET /api/notes list called, uid={}, page={}, size={}, q={}, archived={}, isPublic={}, mineOnly={}",
                uid, page, size, q, archived, isPublic, mineOnly);
        Page<NoteItem> p = noteService.list(uid, page, size, q, archived, isPublic, mineOnly);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    @PostMapping
    /**
     * 新建便签。
     *
     * @param req 创建请求体，包含内容、标签、颜色、是否归档、是否公开等字段
     * @return 200 OK，返回新建后的便签实体
     *
     * 安全与校验：
     * - 需要登录；未登录应返回 401（由服务层或全局异常处理负责）；
     * - 字段校验由 {@code @Valid} 与服务层共同保证，异常统一转为 400。
     */
    public ResponseEntity<Note> create(@Valid @RequestBody NoteRequest req) {
        Long uid = AuthUtil.currentUserId();
        // 说明：避免直接访问请求体的字段导致编译问题，仅记录已接收到创建请求与当前用户。
        log.info("[NoteController] POST /api/notes create called, uid={}", uid);
        return ResponseEntity.ok(noteService.create(uid, req));
    }

    @PutMapping("/{id}")
    /**
     * 更新便签。
     *
     * @param id 便签ID
     * @param req 更新请求体，包含内容/标签等可修改字段
     * @return 200 OK，返回更新后的便签实体
     *
     * 安全与并发：
     * - 需要登录，且仅允许作者更新；服务层会校验所有权并抛出相应异常；
     * - 建议前端在编辑页面使用乐观更新策略并处理冲突。
     */
    public ResponseEntity<Note> update(@PathVariable Long id, @Valid @RequestBody NoteRequest req) {
        Long uid = AuthUtil.currentUserId();
        // 说明：同上，避免访问请求体中的具体字段，保留最有用的参数用于审计。
        log.info("[NoteController] PUT /api/notes/{} update called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.update(uid, id, req));
    }

    @DeleteMapping("/{id}")
    /**
     * 删除便签。
     *
     * @param id 便签ID
     * @return 204 No Content，删除成功不返回实体
     *
     * 安全：
     * - 需要登录，且仅允许作者删除；服务层会校验所有权与状态（例如已归档/公开的删除策略）。
     */
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] DELETE /api/notes/{} delete called, uid={}", id, uid);
        noteService.delete(uid, id);
        return ResponseEntity.noContent().build();
    }

    // 点赞接口
    @PostMapping("/{id}/like")
    /**
     * 点赞便签。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "liked": true, "likeCount": 12, ... }
     *
     * 说明：需要登录；重复点赞会保持幂等（依赖服务层实现）。
     */
    public ResponseEntity<Map<String,Object>> like(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：当点击“喜欢”图标触发点赞时，该日志能证明控制层方法被调用，以及当前用户与目标便签ID。
        log.info("[NoteController] POST /api/notes/{}/like called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.like(uid, id));
    }

    @PostMapping("/{id}/unlike")
    /**
     * 取消点赞便签。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "liked": false, "likeCount": 11, ... }
     *
     * 说明：需要登录；未点赞时取消点赞也应保持幂等。
     */
    public ResponseEntity<Map<String,Object>> unlike(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/unlike called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.unlike(uid, id));
    }

    @GetMapping("/{id}/likes")
    /**
     * 查询便签点赞信息。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "liked": true/false, "likeCount": N, ... }
     *
     * 说明：匿名用户的 {@code liked=false}，登录用户依据自己的点赞记录返回。
     */
    public ResponseEntity<Map<String,Object>> likeInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/{}/likes called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.likeInfo(uid, id));
    }

    // 收藏接口
    @PostMapping("/{id}/favorite")
    /**
     * 收藏便签。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "favorited": true, "favoriteCount": 8, ... }
     *
     * 说明：需要登录；重复收藏保持幂等。
     */
    public ResponseEntity<Map<String,Object>> favorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/favorite called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.favorite(uid, id));
    }

    @PostMapping("/{id}/unfavorite")
    /**
     * 取消收藏便签。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "favorited": false, "favoriteCount": 7, ... }
     *
     * 说明：需要登录；未收藏时取消收藏也保持幂等。
     */
    public ResponseEntity<Map<String,Object>> unfavorite(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] POST /api/notes/{}/unfavorite called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.unfavorite(uid, id));
    }

    @GetMapping("/{id}/favorites")
    /**
     * 查询便签收藏信息。
     *
     * @param id 便签ID
     * @return 200 OK，形如：{ "favorited": true/false, "favoriteCount": N, ... }
     *
     * 说明：匿名用户的 {@code favorited=false}，登录用户依据自己的收藏记录返回。
     */
    public ResponseEntity<Map<String,Object>> favoriteInfo(@PathVariable Long id) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/{}/favorites called, uid={}", id, uid);
        return ResponseEntity.ok(noteService.favoriteInfo(uid, id));
    }

    @GetMapping("/favorites")
    /**
     * 我收藏的便签（分页）。
     *
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10
     * @param q    关键字过滤（可选）
     * @return 200 OK，分页结果
     *
     * 说明：未登录时仅返回公开内容；登录时返回个人收藏列表，并附带用户态标记。
     */
    public ResponseEntity<PageResult<NoteItem>> listFavorited(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        log.info("[NoteController] GET /api/notes/favorites called, uid={}, page={}, size={}, q={} ", uid, page, size, q);
        Page<NoteItem> p = noteService.listFavorited(uid, page, size, q);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    /**
     * 列出当前用户点过赞的便签（分页）
     * 说明：
     * - 与 /favorites 类似，通过用户点赞记录反查便签并分页返回；
     * - 返回的每条记录包含作者昵称、点赞/收藏数量以及“我是否已点赞/已收藏”等用户态标记；
     * - GET 接口允许匿名访问，但仅在携带 token 时才能计算“我是否已点赞/已收藏”，匿名时两者均为 false。
     */
    @GetMapping("/liked")
    public ResponseEntity<PageResult<NoteItem>> listLiked(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "10") int size,
                                                @RequestParam(required = false) String q) {
        Long uid = AuthUtil.currentUserId();
        // 详细注释：用于确认“喜欢页”列表请求是否抵达控制器，若 uid 为空（匿名）则只返回公开数据；携带 token 则可计算用户态标记。
        log.info("[NoteController] GET /api/notes/liked called, uid={}, page={}, size={}, q={} ", uid, page, size, q);
        Page<NoteItem> p = noteService.listLiked(uid, page, size, q);
        PageResult<NoteItem> resp = new PageResult<>();
        resp.setItems(p.getRecords());
        resp.setTotal(p.getTotal());
        resp.setPage(p.getCurrent());
        resp.setSize(p.getSize());
        return ResponseEntity.ok(resp);
    }

    // 已移除：最近/热门公开拾言端点
    // 说明：按需求彻底下线“最近便签”和“热门便签”功能，因此删除
    // GET /api/notes/recent 与 GET /api/notes/hot 两个路由及对应服务方法调用。
    // 这样可以避免无效接口暴露与误用，同时确保控制层不再依赖已移除的服务逻辑。

    /**
     * 导入拾言（批量）
     * 说明：
     * - 前端请求：POST /shiyan/import（axios baseURL 已含 /api，实际路径为 /api/shiyan/import）；
     * - 请求体结构：{ items: NoteRequest[] }，每项包含 content、tags、color、archived、isPublic；
     * - 返回体建议：{ imported: 成功数量, failed: 失败数量, errors: [可选错误消息] }；
     * - 登录校验：仅登录用户允许导入（当前控制器所有方法均依赖 AuthUtil.currentUserId 识别登录态）。
     *
     * HttpRequestMethodNotSupportedException 根因与修复：
     * - 根因：此前后端未提供该 POST 映射，导致 Spring 抛出 405 并封装为该异常；
     * - 修复：新增 @PostMapping("/import") 映射，使 /api/notes/import 与 /api/shiyan/import 均可访问。
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importNotes(@RequestBody ImportNotesRequest req) {
        Long uid = AuthUtil.currentUserId();
        // 详细日志：记录导入请求到达控制器与候选条目数，便于联调定位问题
        int size = (req == null || req.getItems() == null) ? 0 : req.getItems().size();
        log.info("[NoteController] POST /api/notes/import called, uid={}, items={}", uid, size);
        // 未登录直接返回 401（与前端 suppress401Redirect 对齐）：避免将匿名导入写入数据库
        if (uid == null) {
            return ResponseEntity.status(401).body(Map.of("message", "请先登录后再导入"));
        }
        Map<String, Object> result = noteService.importNotes(uid, req == null ? java.util.Collections.emptyList() : req.getItems());
        return ResponseEntity.ok(result);
    }
}