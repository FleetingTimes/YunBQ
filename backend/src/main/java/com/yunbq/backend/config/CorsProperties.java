package com.yunbq.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {
    private List<String> allowedOrigins;
    private List<String> allowedOriginPatterns;
    private List<String> allowedMethods;
    private List<String> allowedHeaders;
    private Boolean allowCredentials = true;

    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public List<String> getAllowedOriginPatterns() { return allowedOriginPatterns; }
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) { this.allowedOriginPatterns = allowedOriginPatterns; }

    public List<String> getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(List<String> allowedMethods) { this.allowedMethods = allowedMethods; }

    public List<String> getAllowedHeaders() { return allowedHeaders; }
    public void setAllowedHeaders(List<String> allowedHeaders) { this.allowedHeaders = allowedHeaders; }

    public Boolean getAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(Boolean allowCredentials) { this.allowCredentials = allowCredentials; }
}