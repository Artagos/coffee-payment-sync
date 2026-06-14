package com.coffeeplace.sync.rest;

import com.coffeeplace.sync.csv.CsvPaymentRecord;
import com.coffeeplace.sync.csv.CsvReader;
import com.coffeeplace.sync.rest.dto.SyncPaymentRequest;
import com.coffeeplace.sync.rest.dto.SyncReportResponse;
import com.coffeeplace.sync.rest.dto.SyncRequest;
import com.coffeeplace.sync.sync.PaymentSyncService;
import com.coffeeplace.sync.sync.SyncReport;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentSyncController {

    private final CsvReader csvReader;
    private final PaymentSyncService syncService;

    public PaymentSyncController(CsvReader csvReader, PaymentSyncService syncService) {
        this.csvReader = csvReader;
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncReportResponse> syncPayments(@RequestBody SyncRequest request) {
        List<CsvPaymentRecord> records = request.payments().stream()
                .map(this::toCsvRecord)
                .toList();

        SyncReport report = syncService.sync(records);
        return ResponseEntity.ok(toResponse(report));
    }

    @PostMapping("/sync-from-csv")
    public ResponseEntity<SyncReportResponse> syncFromCsv(@RequestParam("file") MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile("payments-", ".csv");
        try {
            file.transferTo(tempFile);
            List<CsvPaymentRecord> records = csvReader.read(tempFile);
            SyncReport report = syncService.sync(records);
            return ResponseEntity.ok(toResponse(report));
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private CsvPaymentRecord toCsvRecord(SyncPaymentRequest p) {
        String loyaltyCardId = p.loyaltyCardId() != null ? p.loyaltyCardId() : "";
        String idempotencyKey = p.idempotencyKey() != null && !p.idempotencyKey().isEmpty()
                ? p.idempotencyKey()
                : UUID.randomUUID().toString();
        return new CsvPaymentRecord(p.coffeeType(), p.price(), p.currency(), loyaltyCardId, idempotencyKey);
    }

    private SyncReportResponse toResponse(SyncReport report) {
        long success = report.entries().stream().filter(SyncReport.SyncEntry::success).count();
        List<SyncReportResponse.SyncEntryResponse> entries = report.entries().stream()
                .map(e -> new SyncReportResponse.SyncEntryResponse(
                        e.coffeeType(), e.price(), e.currency(), e.success(), e.paymentId(), e.errorMessage()))
                .toList();
        return new SyncReportResponse(
                report.entries().size(),
                (int) success,
                report.entries().size() - (int) success,
                entries
        );
    }
}
