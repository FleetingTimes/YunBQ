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
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
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