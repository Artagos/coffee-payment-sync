package com.coffeeplace.sync.rest.dto;

import java.util.UUID;

public record AsyncStatusResponse(
        UUID requestId,
        String status,
        int totalCount,
        int completedCount,
        int failedCount
) {}
