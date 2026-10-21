package com.group7.shop.repository;

import com.group7.shop.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 客户账号数据访问（李宇轩负责）。 */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** 登录用：用户名忽略大小写匹配，避免注册 "Tom" 后无法用 "tom" 登录。 */
    Optional<Customer> findByUsernameIgnoreCase(String username);

    /** 注册判重用：忽略大小写，保证 "Tom" 与 "tom" 不能同时注册。 */
    boolean existsByUsernameIgnoreCase(String username);

    /** 拦截器用：按 token 定位当前客户。 */
    Optional<Customer> findByToken(String token);
}
