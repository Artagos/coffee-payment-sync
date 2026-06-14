package com.coffeeplace.sync.service;

import com.coffeeplace.sync.client.PaymentApiClient;
import com.coffeeplace.sync.config.ShardContextHolder;
import com.coffeeplace.sync.csv.CsvPaymentRecord;
import com.coffeeplace.sync.model.PaymentStatus;
import com.coffeeplace.sync.model.metadata.BulkPaymentRequest;
import com.coffeeplace.sync.model.shard.PaymentEntry;
import com.coffeeplace.sync.repository.metadata.BulkPaymentRequestRepository;
import com.coffeeplace.sync.repository.shard.PaymentEntryRepository;
import com.coffeeplace.sync.rest.dto.AsyncStatusResponse;
import com.coffeeplace.sync.rest.dto.SyncPaymentRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AsyncPaymentService {

    private final BulkPaymentRequestRepository bulkRepo;
    private final PaymentEntryRepository paymentEntryRepo;
    private final PaymentApiClient apiClient;
    private final int shardCount;

    public AsyncPaymentService(
            BulkPaymentRequestRepository bulkRepo,
            PaymentEntryRepository paymentEntryRepo,
            PaymentApiClient apiClient,
            @Value("${payment.shard.count}") int shardCount) {
        this.bulkRepo = bulkRepo;
        this.paymentEntryRepo = paymentEntryRepo;
        this.apiClient = apiClient;
        this.shardCount = shardCount;
    }

    @Transactional(transactionManager = "metadataTransactionManager")
    public UUID submitBulkRequest(List<SyncPaymentRequest> payments) {
        UUID requestId = UUID.randomUUID();
        BulkPaymentRequest request = new BulkPaymentRequest(requestId, payments.size());
        bulkRepo.save(request);

        for (SyncPaymentRequest p : payments) {
            String idempotencyKey = p.idempotencyKey() != null && !p.idempotencyKey().isEmpty()
                    ? p.idempotencyKey()
                    : UUID.randomUUID().toString();
            String loyaltyCardId = p.loyaltyCardId() != null ? p.loyaltyCardId() : "";
            int shardKey = Math.abs(idempotencyKey.hashCode() % shardCount);

            PaymentEntry entry = new PaymentEntry(
                    UUID.randomUUID(),
                    requestId,
                    p.coffeeType(),
                    p.price(),
                    p.currency(),
                    loyaltyCardId,
                    idempotencyKey,
                    shardKey
            );

            ShardContextHolder.set(shardKey);
            try {
                paymentEntryRepo.save(entry);
            } finally {
                ShardContextHolder.clear();
            }
        }

        processBulkRequest(requestId);
        return requestId;
    }

    @Async("paymentTaskExecutor")
    public void processBulkRequest(UUID requestId) {
        BulkPaymentRequest request = bulkRepo.findById(requestId).orElse(null);
        if (request == null || request.getStatus() != PaymentStatus.PENDING) {
            return;
        }

        request.setStatus(PaymentStatus.PROCESSING);
        bulkRepo.save(request);

        for (int shard = 0; shard < shardCount; shard++) {
            ShardContextHolder.set(shard);
            try {
                List<PaymentEntry> entries = paymentEntryRepo
                        .findByBulkRequestIdAndStatus(requestId, PaymentStatus.PENDING);
                for (PaymentEntry entry : entries) {
                    processEntry(request, entry);
                }
            } finally {
                ShardContextHolder.clear();
            }
        }

        request.setStatus(PaymentStatus.COMPLETED);
        bulkRepo.save(request);
    }

    private void processEntry(BulkPaymentRequest request, PaymentEntry entry) {
        ShardContextHolder.set(entry.getShardKey());
        try {
            CsvPaymentRecord record = new CsvPaymentRecord(
                    entry.getCoffeeType(),
                    entry.getPrice(),
                    entry.getCurrency(),
                    entry.getLoyaltyCardId(),
                    entry.getIdempotencyKey()
            );

            PaymentApiClient.SendResult result = apiClient.send(record);

            if (result.isSuccess()) {
                String paymentId = result.body() != null ? result.body().paymentId() : null;
                entry.markCompleted(paymentId);
                paymentEntryRepo.save(entry);
                request.incrementCompleted();
            } else {
                String error = result.errorMessage() != null
                        ? result.errorMessage()
                        : "HTTP " + result.statusCode();
                entry.markFailed(error);
                paymentEntryRepo.save(entry);
                request.incrementFailed();
            }

            bulkRepo.save(request);
        } finally {
            ShardContextHolder.clear();
        }
    }

    public AsyncStatusResponse getStatus(UUID requestId) {
        BulkPaymentRequest request = bulkRepo.findById(requestId).orElse(null);
        if (request == null) {
            return null;
        }
        return new AsyncStatusResponse(
                request.getId(),
                request.getStatus().name(),
                request.getTotalCount(),
                request.getCompletedCount(),
                request.getFailedCount()
        );
    }
}
