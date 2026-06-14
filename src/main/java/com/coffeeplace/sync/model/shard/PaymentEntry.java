package com.coffeeplace.sync.model.shard;

import com.coffeeplace.sync.model.PaymentStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_entries")
public class PaymentEntry {

    @Id
    private UUID id;

    @Column(name = "bulk_request_id", nullable = false)
    private UUID bulkRequestId;

    @Column(name = "coffee_type", nullable = false, length = 50)
    private String coffeeType;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "loyalty_card_id", length = 100)
    private String loyaltyCardId;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "shard_key", nullable = false)
    private int shardKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "payment_id", length = 255)
    private String paymentId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentEntry() {}

    public PaymentEntry(UUID id, UUID bulkRequestId, String coffeeType, double price,
                        String currency, String loyaltyCardId, String idempotencyKey, int shardKey) {
        this.id = id;
        this.bulkRequestId = bulkRequestId;
        this.coffeeType = coffeeType;
        this.price = price;
        this.currency = currency;
        this.loyaltyCardId = loyaltyCardId;
        this.idempotencyKey = idempotencyKey;
        this.shardKey = shardKey;
        this.status = PaymentStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBulkRequestId() { return bulkRequestId; }
    public String getCoffeeType() { return coffeeType; }
    public double getPrice() { return price; }
    public String getCurrency() { return currency; }
    public String getLoyaltyCardId() { return loyaltyCardId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public int getShardKey() { return shardKey; }
    public PaymentStatus getStatus() { return status; }
    public String getPaymentId() { return paymentId; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markCompleted(String paymentId) {
        this.status = PaymentStatus.COMPLETED;
        this.paymentId = paymentId;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String errorMessage) {
        this.status = PaymentStatus.FAILED;
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now();
    }
}
