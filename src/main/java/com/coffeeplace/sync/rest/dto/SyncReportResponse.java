package com.coffeeplace.sync.rest.dto;

import java.util.List;

public record SyncReportResponse(
        int total,
        int okCount,
        int failedCount,
        List<SyncEntryResponse> entries
) {
    public record SyncEntryResponse(
            String coffeeType,
            double price,
            String currency,
            boolean success,
            String paymentId,
            String errorMessage
    ) {}
}
