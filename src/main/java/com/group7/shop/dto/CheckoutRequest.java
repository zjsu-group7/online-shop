package com.group7.shop.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CheckoutRequest(@NotEmpty List<Long> cartItemIds) {}
