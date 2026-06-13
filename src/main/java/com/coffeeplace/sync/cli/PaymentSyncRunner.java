package com.coffeeplace.sync.cli;

import com.coffeeplace.sync.csv.CsvPaymentRecord;
import com.coffeeplace.sync.csv.CsvReader;
import com.coffeeplace.sync.sync.PaymentSyncService;
import com.coffeeplace.sync.sync.SyncReport;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class PaymentSyncRunner implements CommandLineRunner {

    private final CsvReader csvReader;
    private final PaymentSyncService syncService;

    public PaymentSyncRunner(CsvReader csvReader, PaymentSyncService syncService) {
        this.csvReader = csvReader;
        this.syncService = syncService;
    }

    @Override
    public void run(String... args) throws Exception {
        if (args.length == 0) {
            return;
        }

        Path csvPath = Path.of(args[0]);
        if (!csvPath.toFile().exists()) {
            System.err.println("File not found: " + csvPath.toAbsolutePath());
            System.exit(1);
        }

        System.out.println("Reading payments from: " + csvPath.toAbsolutePath());
        List<CsvPaymentRecord> records = csvReader.read(csvPath);
        System.out.println("Found " + records.size() + " payment(s) to sync");
        System.out.println();

        SyncReport report = syncService.sync(records);
        report.print();

        long failed = report.entries().stream().filter(e -> !e.success()).count();
        System.exit(failed > 0 ? 1 : 0);
    }
}
