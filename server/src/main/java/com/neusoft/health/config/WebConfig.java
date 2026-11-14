package com.neusoft.health.config;

import com.neusoft.health.dto.BusinessException;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(
            new HandlerInterceptor() {
              @Override
              public boolean preHandle(
                  HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Cache-Control", "no-store");
                var session = request.getSession(true);
                if (session.getAttribute("csrf") == null)
                  session.setAttribute("csrf", UUID.randomUUID().toString());
                if (!Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) {
                  String token = request.getHeader("X-CSRF-Token");
                  if (token == null || !token.equals(session.getAttribute("csrf")))
                    throw new BusinessException(403, "请求校验失败，请刷新页面后重试");
                }
                return true;
              }
            })
        .addPathPatterns("/tijian/**", "/tijiancms/**");
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**")
        .allowedOrigins(
            "http://localhost:8081",
            "http://localhost:8089",
            "http://127.0.0.1:8081",
            "http://127.0.0.1:8089")
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("Content-Type", "X-CSRF-Token")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
