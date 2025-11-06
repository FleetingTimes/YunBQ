package com.yunbq.backend.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BackgroundController {
    private final Random random = new Random();

    @CrossOrigin(origins = "http://localhost:5173")
    @GetMapping("/background")
    /**
     * 返回随机背景图 URL（代理第三方图片服务）。
     *
     * @param w 目标宽度，默认 1920，非正数将回退为 1920
     * @param h 目标高度，默认 1080，非正数将回退为 1080
     * @param q 可选关键词（逗号分隔），为空时使用默认 {@code nature,landscape,wallpaper}
     * @return 200 OK，形如：{ "url": "https://..." }
     *
     * 行为：
     * - 从候选源（picsum/unsplash/固定壁纸）中随机选择一个 URL 返回；
     * - 该接口仅返回链接，不代理图片内容；前端负责加载与缓存。
     *
     * 跨域：
     * - 允许来自 {@code http://localhost:5173} 的跨域请求，用于本地开发预览。
     *
     * 风险与限制：
     * - 第三方源可能出现不可用或速率限制；前端应做好失败回退与重试；
     * - 固定壁纸作为兜底，确保在第三方不可用时仍有稳定返回。
     */
    public Map<String, String> randomBackground(
            @RequestParam(name = "w", defaultValue = "1920") int w,
            @RequestParam(name = "h", defaultValue = "1080") int h,
            @RequestParam(name = "q", required = false) String q
    ) {
        if (w <= 0) w = 1920;
        if (h <= 0) h = 1080;
        long seed = System.currentTimeMillis() ^ random.nextLong();
        String query = (q == null || q.isBlank()) ? "nature,landscape,wallpaper" : q;

        List<String> candidates = new ArrayList<>();
        candidates.add(String.format("https://picsum.photos/%d/%d?random=%d", w, h, seed));
        candidates.add(String.format("https://source.unsplash.com/random/%dx%d/?%s", w, h, query));
        // a fixed beautiful wallpaper as a stable fallback
        candidates.add(String.format("https://images.unsplash.com/photo-1519681393784-d120267933ba?w=%d&h=%d&fit=crop", w, h));

        String url = candidates.get(random.nextInt(candidates.size()));
        Map<String, String> res = new HashMap<>();
        res.put("url", url);
        return res;
    }
}