package com.yunbq.backend.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;
    private final com.yunbq.backend.config.CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter, com.yunbq.backend.config.CorsProperties corsProperties) {
        this.jwtFilter = jwtFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // .anonymous(anonymous -> anonymous.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/captcha/**").permitAll()
                .requestMatchers("/api/debug/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                // 允许匿名访问公开便签的查询接口
                .requestMatchers(HttpMethod.GET, "/api/notes").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/notes/**").permitAll()
                // 显式放行 liked 列表（用于列出“我点赞的便签”）。
                // 理论上上面的通配规则已覆盖，但在当前环境下 /api/notes/liked 返回 401，
                // 为确保行为符合预期，这里补充精确路径的放行以规避匹配异常。
                .requestMatchers(HttpMethod.GET, "/api/notes/liked").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // —— 管理端接口限制 ——
                // 说明：除方法级 @PreAuthorize("hasRole('ADMIN')") 外，
                // 在路径层面也限制 /api/navigation/admin/** 与 /api/admin/**，实现双保险。
                // 注意：该匹配需置于公开放行规则之前，避免被更宽的放行规则覆盖。
                .requestMatchers("/api/navigation/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // —— 导航系统公开接口放行 ——
                // 说明：导航页面需要在未登录时也能浏览分类与站点，因此放行以下方法：
                // - GET：页面数据获取
                // - HEAD：部分客户端会在 GET 前发送 HEAD 探测请求
                // 为防止方法匹配导致的 401，这里同时放行 GET 与 HEAD。
                .requestMatchers(HttpMethod.GET, "/api/navigation/**").permitAll()
                .requestMatchers(HttpMethod.HEAD, "/api/navigation/**").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                .accessDeniedHandler((req, res, ex) -> res.sendError(HttpServletResponse.SC_FORBIDDEN))
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // allow credentials
        if (Boolean.TRUE.equals(corsProperties.getAllowCredentials())) {
            config.setAllowCredentials(true);
        }
        // origins or patterns
        var patterns = corsProperties.getAllowedOriginPatterns();
        var origins = corsProperties.getAllowedOrigins();
        if (patterns != null && !patterns.isEmpty()) {
            config.setAllowedOriginPatterns(patterns);
        } else if (origins != null && !origins.isEmpty()) {
            config.setAllowedOrigins(origins);
        } else {
            // fallback for dev：补充常用端口（5176/5180）
            config.setAllowedOrigins(java.util.List.of(
                "http://localhost:5500",
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://localhost:5176",
                "http://localhost:5180"
            ));
        }
        // methods
        var methods = corsProperties.getAllowedMethods();
        config.setAllowedMethods(methods != null && !methods.isEmpty() ? methods : java.util.List.of("GET","POST","PUT","DELETE","OPTIONS"));
        // headers
        var headers = corsProperties.getAllowedHeaders();
        config.setAllowedHeaders(headers != null && !headers.isEmpty() ? headers : java.util.List.of("Authorization","Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}