package com.group7.shop.common;

import com.group7.shop.entity.Customer;
import org.springframework.http.HttpStatus;

/**
 * 账号隔离守卫（李宇轩负责，挑战需求 B 的账号权限要求）。
 *
 * <p>收藏、购物车、订单都是「属于某个客户」的数据。全组的统一口径是：
 * <ol>
 *   <li><b>身份只从 token 取</b>：{@code customerId} 一律来自
 *       {@code CustomerAuthInterceptor} 写入的 request attribute，任何接口都不接受
 *       请求体/URL 里的 customerId；</li>
 *   <li><b>归属必须二次核对</b>：即使 service 已按 customerId 过滤，
 *       对单条资源的写操作（取消订单、删除购物车项、取消收藏）仍要核对记录的归属，
 *       用到本条守卫。</li>
 * </ol>
 *
 * <p>越权统一返回 **403**（身份合法但无权操作该资源）；
 * 若泄露「该订单存在」会造成订单号被枚举，因此对查不到的记录，业务侧应返回 404 而不是 403。
 */
public final class AccountGuard {

    private AccountGuard() {
    }

    /**
     * 校验当前登录客户是否为资源的归属人，否则抛 403。
     *
     * @param resource        资源名称，用于错误提示（如「订单」「购物车条目」「收藏」）
     * @param ownerCustomerId 资源记录的归属客户 id，记录缺失时传 {@code null}
     * @param current         当前登录客户（由拦截器注入，非空）
     */
    public static void requireOwner(String resource, Long ownerCustomerId, Customer current) {
        if (current == null || current.getId() == null) {
            throw BusinessException.unauthorized("未登录或登录已失效");
        }
        if (ownerCustomerId == null || !ownerCustomerId.equals(current.getId())) {
            throw forbidden(resource);
        }
    }

    /** 便捷重载：直接用两个 id 比较。 */
    public static void requireOwner(String resource, Long ownerCustomerId, Long currentCustomerId) {
        if (currentCustomerId == null) {
            throw BusinessException.unauthorized("未登录或登录已失效");
        }
        if (ownerCustomerId == null || !ownerCustomerId.equals(currentCustomerId)) {
            throw forbidden(resource);
        }
    }

    /** 越权提示不携带资源细节，避免被用来探测他人数据。 */
    private static BusinessException forbidden(String resource) {
        return new BusinessException(HttpStatus.FORBIDDEN, "无权操作他人的" + resource);
    }
}
