package com.coffeeplace.sync.rest.dto;

public record SyncPaymentRequest(
        String coffeeType,
        double price,
        String currency,
        String loyaltyCardId,
        String idempotencyKey
) {}
