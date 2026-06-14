package com.coffeeplace.sync.rest;

import com.coffeeplace.sync.rest.dto.*;
import com.coffeeplace.sync.service.AsyncPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class AsyncPaymentController {

    private final AsyncPaymentService asyncPaymentService;

    public AsyncPaymentController(AsyncPaymentService asyncPaymentService) {
        this.asyncPaymentService = asyncPaymentService;
    }

    @PostMapping("/async")
    public ResponseEntity<AsyncSubmitResponse> submitAsync(@RequestBody SyncRequest request) {
        UUID requestId = asyncPaymentService.submitBulkRequest(request.payments());
        return ResponseEntity.ok(new AsyncSubmitResponse(requestId));
    }

    @GetMapping("/async/{requestId}")
    public ResponseEntity<AsyncStatusResponse> getStatus(@PathVariable UUID requestId) {
        AsyncStatusResponse status = asyncPaymentService.getStatus(requestId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
