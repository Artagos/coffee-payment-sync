package com.coffeeplace.sync.csv;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class CsvReader {

    public List<CsvPaymentRecord> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<CsvPaymentRecord> records = new ArrayList<>();
        boolean header = true;
        for (String line : lines) {
            if (header) {
                header = false;
                continue;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            records.add(parseLine(line));
        }
        return records;
    }

    private CsvPaymentRecord parseLine(String line) {
        String[] parts = line.split(",");
        String coffeeType = parts[0].trim();
        double price = Double.parseDouble(parts[1].trim());
        String currency = parts[2].trim();
        String loyaltyCardId = parts.length > 3 ? parts[3].trim() : "";
        String idempotencyKey = parts.length > 4 && !parts[4].trim().isEmpty()
                ? parts[4].trim()
                : UUID.randomUUID().toString();
        return new CsvPaymentRecord(coffeeType, price, currency, loyaltyCardId, idempotencyKey);
    }
}
