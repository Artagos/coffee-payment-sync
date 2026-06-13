package com.coffeeplace.sync.rest.dto;

import java.util.List;

public record SyncRequest(
        List<SyncPaymentRequest> payments
) {}
