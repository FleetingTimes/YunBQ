package com.yunbq.backend.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        boolean bearer = header != null && header.startsWith("Bearer ");
        log.debug("JWT filter enter: uri={} headerPresent={} bearerPrefix={}", request.getRequestURI(), header != null, bearer);
        if (bearer) {
            String token = header.substring(7);
            try {
                DecodedJWT jwt = jwtUtil.verify(token);
                Long uid = jwt.getClaim("uid").asLong();
                String uname = jwt.getClaim("uname").asString();
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(uid, null, Collections.emptyList());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT ok: uid={} uname={} uri={}", uid, uname, request.getRequestURI());
            } catch (Exception e) {
                log.warn("JWT verification failed: uri={} msg={}", request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else if (header != null) {
            log.debug("Authorization header present but not Bearer: uri={} header={}", request.getRequestURI(), header);
        } else {
            log.debug("No Authorization header: uri={}", request.getRequestURI());
        }
        filterChain.doFilter(request, response);
    }
}