package com.coffeeplace.sync.csv;

public record CsvPaymentRecord(
        String coffeeType,
        double price,
        String currency,
        String loyaltyCardId,
        String idempotencyKey
) {}
