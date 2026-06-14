package com.coffeeplace.sync.repository.metadata;

import com.coffeeplace.sync.model.metadata.BulkPaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BulkPaymentRequestRepository extends JpaRepository<BulkPaymentRequest, UUID> {
}
