package com.group7.shop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册客户端鉴权拦截器：覆盖 /api/customer/**，放行注册与登录。
 * 与卖家拦截器并存，两者路径与 request attribute 互不交叉。
 */
@Configuration
public class CustomerAuthWebConfig implements WebMvcConfigurer {

    private final CustomerAuthInterceptor customerAuthInterceptor;

    public CustomerAuthWebConfig(CustomerAuthInterceptor customerAuthInterceptor) {
        this.customerAuthInterceptor = customerAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(customerAuthInterceptor)
                .addPathPatterns("/api/customer/**")
                .excludePathPatterns("/api/customer/register", "/api/customer/login");
    }
}
