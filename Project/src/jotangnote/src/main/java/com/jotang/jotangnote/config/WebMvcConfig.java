package com.jotang.jotangnote.config;

import com.jotang.jotangnote.common.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：把登录拦截器挂到指定路径上。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer
{
    private final AuthInterceptor authInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor)
    {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/notes/**");   // 只拦笔记相关的接口
    }
}
