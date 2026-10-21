package com.group7.shop.controller;

import com.group7.shop.common.Result;
import com.group7.shop.config.CustomerAuthInterceptor;
import com.group7.shop.dto.CreateOrderResponse;
import com.group7.shop.dto.CustomerLoginResponse;
import com.group7.shop.dto.CustomerView;
import com.group7.shop.dto.LoginRequest;
import com.group7.shop.dto.OrderHistory;
import com.group7.shop.dto.RegisterRequest;
import com.group7.shop.dto.SubmitIntentRequest;
import com.group7.shop.dto.UpdateProfileRequest;
import com.group7.shop.entity.Customer;
import com.group7.shop.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户（买家）账号与个人中心接口（李宇轩负责，挑战需求 A.2.1 / A.2.2）。
 *
 * <pre>
 * POST /api/customer/register  注册（用户名唯一、密码加盐哈希）
 * POST /api/customer/login     登录，返回 token 与过期时间
 * POST /api/customer/logout    退出，token 立即失效
 * GET  /api/customer/me        个人中心
 * PUT  /api/customer/me        修改电话与默认交易地点（选填）
 * POST /api/customer/orders    登录客户下单（排队逻辑复用意向域）
 * GET  /api/customer/orders    我的下单记录（仅成功 / 失败）
 * </pre>
 *
 * 除注册与登录外均由 {@link CustomerAuthInterceptor} 校验 X-Token，
 * 已认证客户通过 request attribute {@code authenticatedCustomer} 注入，
 * 本类不解析请求头，也不接受任何形式的 customerId 入参，杜绝越权查询他人记录。
 */
@RestController
@RequestMapping("/api/customer")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping("/register")
    public Result<CustomerView> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success("注册成功", customerService.register(request));
    }

    @PostMapping("/login")
    public Result<CustomerLoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", customerService.login(request.username(), request.password()));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        customerService.logout(customer);
        return Result.success("已退出登录", null);
    }

    @GetMapping("/me")
    public Result<CustomerView> me(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        return Result.success(customerService.profile(customer));
    }

    @PutMapping("/me")
    public Result<CustomerView> updateProfile(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        return Result.success("资料已更新", customerService.updateProfile(customer, request));
    }

    @GetMapping("/orders")
    public Result<OrderHistory> orders(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer) {
        return Result.success(customerService.orders(customer));
    }

    /**
     * 登录客户下单。排队、口令码、商品状态校验全部复用意向域实现，
     * 本接口只额外记录「这条意向属于当前客户」，不修改意向域代码。
     */
    @PostMapping("/orders")
    public Result<CreateOrderResponse> createOrder(@RequestAttribute(CustomerAuthInterceptor.ATTRIBUTE) Customer customer,
                                                   @RequestBody SubmitIntentRequest request) {
        return Result.success("下单成功", customerService.createOrder(customer, request));
    }
}
