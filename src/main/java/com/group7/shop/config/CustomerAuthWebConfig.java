package com.group7.shop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册客户端鉴权拦截器（李宇轩负责）。
 *
 * <p>迭代 3（挑战需求 B）把「登录 + 账号隔离」作为硬性要求：收藏、购物车、订单都带客户私有数据，
 * 因此拦截范围从最初的 {@code /api/customer/**} 扩展为四类路径：
 * <ul>
 *   <li>{@code /api/customer/**} —— 账号与个人中心（放行注册与登录）；</li>
 *   <li>{@code /api/favorites/**} —— 收藏（华天遥）；</li>
 *   <li>{@code /api/cart/**} —— 购物车（华天遥）；</li>
 *   <li>{@code /api/orders/**} —— 正式订单（李宇轩 + 周琳皓）。</li>
 * </ul>
 *
 * <p>新增路径**只需在此登记**，对应 Controller 不必自带鉴权代码，避免出现「某个接口漏校验」。
 * 未登录时拦截器在进入 Controller 之前就返回 401，因此这些路径下的接口只要被访问就必须带
 * {@code X-Token}。与卖家拦截器并存，两者路径与 request attribute 互不交叉。
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
                .addPathPatterns(
                        "/api/customer/**",
                        "/api/favorites/**",
                        "/api/cart/**",
                        "/api/orders/**"
                )
                .excludePathPatterns(
                        "/api/customer/register",
                        "/api/customer/login"
                );
    }
}
