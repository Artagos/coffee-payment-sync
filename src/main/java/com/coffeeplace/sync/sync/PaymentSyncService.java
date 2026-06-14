package com.coffeeplace.sync.sync;

import com.coffeeplace.sync.client.PaymentApiClient;
import com.coffeeplace.sync.csv.CsvPaymentRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentSyncService {

    private final PaymentApiClient apiClient;
    private final int maxRetries;
    private final long retryBackoffMs;

    public PaymentSyncService(
            PaymentApiClient apiClient,
            @Value("${sync.max-retries}") int maxRetries,
            @Value("${sync.retry-backoff-ms}") long retryBackoffMs) {
        this.apiClient = apiClient;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
    }

    public SyncReport sync(List<CsvPaymentRecord> records) {
        SyncReport report = new SyncReport();

        for (CsvPaymentRecord record : records) {
            SyncReport.SyncEntry entry = sendWithRetry(record, 0);
            report.add(entry);
        }

        return report;
    }

    private SyncReport.SyncEntry sendWithRetry(CsvPaymentRecord record, int attempt) {
        PaymentApiClient.SendResult result = apiClient.send(record);

        if (result.isSuccess()) {
            String paymentId = result.body() != null ? result.body().paymentId() : null;
            return new SyncReport.SyncEntry(
                    record.coffeeType(), record.price(), record.currency(),
                    true, paymentId, null);
        }

        if (attempt < maxRetries) {
            System.out.printf("Retry %d/%d for %s %.2f %s (status=%d, error=%s)%n",
                    attempt + 1, maxRetries, record.coffeeType(),
                    record.price(), record.currency(),
                    result.statusCode(), result.errorMessage());
            try {
                Thread.sleep(retryBackoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return sendWithRetry(record, attempt + 1);
        }

        return new SyncReport.SyncEntry(
                record.coffeeType(), record.price(), record.currency(),
                false, null,
                result.errorMessage() != null ? result.errorMessage() : "HTTP " + result.statusCode());
    }
}
