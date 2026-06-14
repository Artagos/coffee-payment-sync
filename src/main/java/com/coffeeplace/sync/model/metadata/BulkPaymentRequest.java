package com.coffeeplace.sync.model.metadata;

import com.coffeeplace.sync.model.PaymentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bulk_payment_requests")
public class BulkPaymentRequest {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BulkPaymentRequest() {}

    public BulkPaymentRequest(UUID id, int totalCount) {
        this.id = id;
        this.status = PaymentStatus.PENDING;
        this.totalCount = totalCount;
        this.completedCount = 0;
        this.failedCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public PaymentStatus getStatus() { return status; }
    public int getTotalCount() { return totalCount; }
    public int getCompletedCount() { return completedCount; }
    public int getFailedCount() { return failedCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(PaymentStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void incrementCompleted() {
        this.completedCount++;
        this.updatedAt = Instant.now();
    }

    public void incrementFailed() {
        this.failedCount++;
        this.updatedAt = Instant.now();
    }
}
