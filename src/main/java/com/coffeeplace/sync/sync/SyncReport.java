package com.coffeeplace.sync.sync;

import java.util.ArrayList;
import java.util.List;

public class SyncReport {

    private final List<SyncEntry> entries = new ArrayList<>();

    public void add(SyncEntry entry) {
        entries.add(entry);
    }

    public List<SyncEntry> entries() {
        return entries;
    }

    public void print() {
        long success = entries.stream().filter(SyncEntry::success).count();
        long failed = entries.size() - success;

        System.out.println();
        System.out.println("=== Payment Sync Report ===");
        System.out.println("Total:  " + entries.size());
        System.out.println("OK:     " + success);
        System.out.println("Failed: " + failed);
        System.out.println();

        for (SyncEntry entry : entries) {
            String status = entry.success() ? "OK" : "FAIL";
            System.out.printf("[%s] %s | %s %.2f %s | id=%s",
                    status,
                    entry.coffeeType(),
                    entry.price(),
                    entry.currency(),
                    entry.paymentId() != null ? entry.paymentId() : "-");
            if (entry.errorMessage() != null) {
                System.out.print(" | error=" + entry.errorMessage());
            }
            System.out.println();
        }
    }

    public record SyncEntry(
            String coffeeType,
            double price,
            String currency,
            boolean success,
            String paymentId,
            String errorMessage
    ) {}
}
