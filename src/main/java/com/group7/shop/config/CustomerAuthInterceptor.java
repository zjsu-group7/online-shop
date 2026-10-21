package com.group7.shop.config;

import com.group7.shop.entity.Customer;
import com.group7.shop.repository.CustomerRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

/**
 * 客户（买家）端 X-Token 登录拦截器（李宇轩负责，挑战需求 A.2.1）。
 *
 * <p>拦截 /api/customer/**，放行 register 与 login；校验请求头 X-Token，
 * 缺失、无效或已过期均返回 401（统一 Result 格式）。
 * 校验通过后把 Customer 写入 request attribute {@value #ATTRIBUTE}，Controller 直接取用。
 *
 * <p>与 {@link SellerAuthInterceptor} 完全隔离：卖家 token 不能访问客户接口，
 * 客户 token 也不能访问 /api/seller/**，避免两套身份串用。
 */
@Component
public class CustomerAuthInterceptor implements HandlerInterceptor {

    /** Controller 通过 @RequestAttribute("authenticatedCustomer") 取当前登录客户。 */
    public static final String ATTRIBUTE = "authenticatedCustomer";

    private static final Logger log = LoggerFactory.getLogger(CustomerAuthInterceptor.class);

    private final CustomerRepository customerRepository;

    public CustomerAuthInterceptor(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String token = request.getHeader("X-Token");
        if (token == null || token.isBlank()) {
            return writeUnauthorized(response);
        }
        Customer customer = customerRepository.findByToken(token).orElse(null);
        if (customer == null) {
            return writeUnauthorized(response);
        }
        Instant expiresAt = customer.getTokenExpiresAt();
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            log.info("客户账号 {} 的 token 已于 {} 过期，拒绝访问 {}",
                    customer.getUsername(), expiresAt, request.getRequestURI());
            return writeUnauthorized(response);
        }
        request.setAttribute(ATTRIBUTE, customer);
        return true;
    }

    private boolean writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已失效\",\"data\":null}");
        return false;
    }
}
