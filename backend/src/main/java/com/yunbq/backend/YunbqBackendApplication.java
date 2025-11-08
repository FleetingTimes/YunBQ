package com.yunbq.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 应用入口与基础 Web 配置
 * - 入口：标准 Spring Boot 启动类，启用定时任务（用于日志保留清理等后台作业）；
 * - 静态资源：通过 `WebMvcConfigurer#addResourceHandlers` 将本地 `uploads` 目录映射为 `/uploads/**`；
 *   其中头像上传保存于 `uploads/avatars`，便于 AccountController 返回可访问的头像 URL。
 */
@SpringBootApplication
@EnableScheduling // 启用 Spring 调度任务，用于日志保留清理等后台作业
@EnableCaching    // 启用 Spring Cache 注解（@Cacheable/@CacheEvict 等），结合 Caffeine 生效
public class YunbqBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(YunbqBackendApplication.class, args);
	}

    /**
     * 注册静态资源映射：将工作目录下的 `uploads` 映射到 HTTP 路径 `/uploads/**`。
     * 说明：
     * - 使用 `file:` 前缀指向本地目录，适合开发环境与单机部署；
     * - 若迁移到对象存储（如 OSS），可替换为 CDN 域名或网关代理规则。
     */
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String baseDir = System.getProperty("user.dir") + "/uploads/";
                registry.addResourceHandler("/uploads/**")
                        .addResourceLocations("file:" + baseDir);
            }
        };
    }
}