package com.coffeeplace.sync.repository.shard;

import com.coffeeplace.sync.model.PaymentStatus;
import com.coffeeplace.sync.model.shard.PaymentEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentEntryRepository extends JpaRepository<PaymentEntry, UUID> {

    List<PaymentEntry> findByBulkRequestIdAndStatus(UUID bulkRequestId, PaymentStatus status);
}
