package com.yunbq.backend.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class BackgroundController {
    private final Random random = new Random();

    @CrossOrigin(origins = "http://localhost:5173")
    @GetMapping("/background")
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