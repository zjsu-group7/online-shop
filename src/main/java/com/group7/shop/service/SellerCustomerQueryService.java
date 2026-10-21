package com.group7.shop.service;

import com.group7.shop.common.BusinessException;
import com.group7.shop.dto.OrderHistory;
import com.group7.shop.dto.SellerCustomerListView;
import com.group7.shop.dto.SellerCustomerOrderHistoryView;
import com.group7.shop.dto.SellerCustomerView;
import com.group7.shop.entity.Customer;
import com.group7.shop.entity.CustomerOrderLink;
import com.group7.shop.entity.Intent;
import com.group7.shop.entity.IntentStatus;
import com.group7.shop.repository.CustomerOrderLinkRepository;
import com.group7.shop.repository.CustomerRepository;
import com.group7.shop.repository.IntentRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 卖家只读客户查询；复用账号域和订单关联，不修改登录或交易状态。 */
@Service
public class SellerCustomerQueryService {

    private final CustomerRepository customerRepository;
    private final CustomerOrderLinkRepository orderLinkRepository;
    private final IntentRepository intentRepository;
    private final CustomerService customerService;

    public SellerCustomerQueryService(CustomerRepository customerRepository,
                                      CustomerOrderLinkRepository orderLinkRepository,
                                      IntentRepository intentRepository,
                                      CustomerService customerService) {
        this.customerRepository = customerRepository;
        this.orderLinkRepository = orderLinkRepository;
        this.intentRepository = intentRepository;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public SellerCustomerListView customers() {
        List<Customer> customers = customerRepository.findAll(Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        List<CustomerOrderLink> links = orderLinkRepository.findAll();
        Map<Long, IntentStatus> statuses = intentRepository.findAllById(
                        links.stream().map(CustomerOrderLink::getIntentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Intent::getId, Intent::getStatus));

        Map<Long, Counts> countsByCustomer = new HashMap<>();
        for (CustomerOrderLink link : links) {
            IntentStatus status = statuses.get(link.getIntentId());
            if (status == IntentStatus.TRADED || status == IntentStatus.FAILED) {
                Counts counts = countsByCustomer.computeIfAbsent(link.getCustomerId(), ignored -> new Counts());
                if (status == IntentStatus.TRADED) {
                    counts.success++;
                } else {
                    counts.failure++;
                }
            }
        }

        List<SellerCustomerView> items = customers.stream()
                .map(customer -> {
                    Counts counts = countsByCustomer.getOrDefault(customer.getId(), new Counts());
                    return new SellerCustomerView(
                            customer.getId(),
                            customer.getUsername(),
                            maskPhone(customer.getPhone()),
                            customer.getCreatedAt(),
                            counts.success,
                            counts.failure,
                            counts.success + counts.failure
                    );
                })
                .toList();
        return new SellerCustomerListView(items, items.size());
    }

    /** 复用买家侧已验证的终态订单聚合，保证两端成功/失败口径一致。 */
    public SellerCustomerOrderHistoryView orders(Long customerId) {
        if (customerId == null || customerId <= 0) {
            throw BusinessException.badRequest("客户 ID 必须为正整数");
        }
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> BusinessException.notFound("客户不存在"));
        OrderHistory history = customerService.orders(customer);
        return new SellerCustomerOrderHistoryView(
                customer.getId(), customer.getUsername(), history.items(), history.total());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String value = phone.trim();
        if (value.length() == 11) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 2)
                + "*".repeat(value.length() - 4)
                + value.substring(value.length() - 2);
    }

    private static final class Counts {
        private int success;
        private int failure;
    }
}
