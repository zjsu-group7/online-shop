package com.group7.shop.order;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.group7.shop.common.BusinessException;

/**
 * {@link OrderGateway} 的取用入口。
 *
 * <p>订单核心域与本域并行开发，合并前容器里可能还没有 {@link OrderGateway} 的实现。
 * 这里用 {@link ObjectProvider} 做**可选依赖**：应用照常启动（前端、部署、其他域的
 * 联调不受影响），只有真正访问订单接口时才返回 503 并给出可读提示。
 * 订单域实现该接口并注册为 Bean 之后，本类无需任何改动即可生效。
 */
@Component
public class OrderGatewayAccessor {

    private final ObjectProvider<OrderGateway> provider;

    public OrderGatewayAccessor(ObjectProvider<OrderGateway> provider) {
        this.provider = provider;
    }

    /** 取订单域实现；尚未合并时抛 503。 */
    public OrderGateway require() {
        OrderGateway gateway = provider.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "订单核心域尚未接入，订单相关接口暂不可用");
        }
        return gateway;
    }
}
