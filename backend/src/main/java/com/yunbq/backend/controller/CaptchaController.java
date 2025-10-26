package com.yunbq.backend.controller;

import com.yunbq.backend.service.CaptchaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/captcha")
public class CaptchaController {
    private final CaptchaService captchaService;
    public CaptchaController(CaptchaService captchaService){ this.captchaService = captchaService; }

    @GetMapping
    public ResponseEntity<Map<String,Object>> get() {
        var c = captchaService.generate();
        return ResponseEntity.ok(Map.of("id", c.id(), "image", c.dataUrl()));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String,Object>> verify(@RequestBody Map<String,String> body) {
        boolean ok = captchaService.verify(body.get("id"), body.get("code"));
        return ResponseEntity.ok(Map.of("valid", ok));
    }
}