package com.yunbq.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootApplication
@EnableScheduling // 启用 Spring 调度任务，用于日志保留清理等后台作业
public class YunbqBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(YunbqBackendApplication.class, args);
	}

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