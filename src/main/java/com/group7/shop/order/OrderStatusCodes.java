package com.group7.shop.order;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单状态码与展示文案（挑战需求 B.1.1 五态 + 取消终态）。
 *
 * <p>状态的**合法迁移由订单核心域判定**，本类只承担两件事：
 * <ol>
 *   <li>把 HTTP 上的 {@code ?status=} 过滤参数收敛在白名单内，杜绝拼错状态却返回空列表；</li>
 *   <li>订单域如果只返回机器码，由本域补齐中文文案，保证前端只有一个展示口径。</li>
 * </ol>
 *
 * <p>顺序即业务流转顺序：客户下单 → 商家确认 → 备货完成 → 开始发货 → 交易完成；取消为终态。
 */
public final class OrderStatusCodes {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String PACKED = "PACKED";
    public static final String SHIPPED = "SHIPPED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    /**
     * 订单核心域 {@code OrderStatus} 枚举名 → 本域状态码。
     *
     * <p>两套命名是历史原因：本域按业务口语命名（PENDING / PACKED），
     * 订单域的枚举按主谓命名（CUSTOMER_SUBMITTED / READY_TO_SHIP）。
     * 前端徽章配色按订单域取值写，`?status=` 过滤参数可能来自任一来源，
     * 因此在入口处统一归一，避免"状态名拼对了却返回空列表"。
     * 长期仍应统一为一套（以订单域枚举为准），此处只是兼容层。
     */
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();

    static {
        LABELS.put(PENDING, "客户下单");
        LABELS.put(CONFIRMED, "商家确认");
        LABELS.put(PACKED, "备货完成");
        LABELS.put(SHIPPED, "开始发货");
        LABELS.put(COMPLETED, "交易完成");
        LABELS.put(CANCELLED, "已取消");

        ALIASES.put("CUSTOMER_SUBMITTED", PENDING);
        ALIASES.put("SELLER_CONFIRMED", CONFIRMED);
        ALIASES.put("READY_TO_SHIP", PACKED);
        /* SHIPPED / COMPLETED / CANCELLED 与订单域同名，无需别名 */
    }

    private OrderStatusCodes() {
    }

    /** 全部状态码，按业务流转顺序。 */
    public static List<String> all() {
        return List.copyOf(LABELS.keySet());
    }

    /** 归一化为本域大写状态码（兼容订单域枚举名），无法识别返回 {@code null}。 */
    public static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String upper = code.trim().toUpperCase();
        if (LABELS.containsKey(upper)) {
            return upper;
        }
        return ALIASES.get(upper);
    }

    /** 中文文案；无法识别的状态原样返回，避免前端出现空白标签。 */
    public static String labelOf(String code) {
        String normalized = normalize(code);
        return normalized == null ? code : LABELS.get(normalized);
    }
}
