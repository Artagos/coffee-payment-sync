package com.coffeeplace.sync.csv;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvReaderTest {

    private final CsvReader reader = new CsvReader();

    @Test
    void shouldParseValidCsv(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("payments.csv");
        Files.writeString(csv, """
                coffeeType,price,currency,loyaltyCardId,idempotencyKey
                LATTE,3.50,EUR,card-001,key-001
                ESPRESSO,2.00,USD,card-002,key-002
                """.trim());

        List<CsvPaymentRecord> records = reader.read(csv);

        assertEquals(2, records.size());

        assertEquals("LATTE", records.get(0).coffeeType());
        assertEquals(3.50, records.get(0).price());
        assertEquals("EUR", records.get(0).currency());
        assertEquals("card-001", records.get(0).loyaltyCardId());
        assertEquals("key-001", records.get(0).idempotencyKey());

        assertEquals("ESPRESSO", records.get(1).coffeeType());
        assertEquals(2.00, records.get(1).price());
    }

    @Test
    void shouldGenerateIdempotencyKeyWhenMissing(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("payments.csv");
        Files.writeString(csv, """
                coffeeType,price,currency,loyaltyCardId
                LATTE,3.50,EUR,card-001
                """.trim());

        List<CsvPaymentRecord> records = reader.read(csv);

        assertEquals(1, records.size());
        assertNotNull(records.get(0).idempotencyKey());
        assertFalse(records.get(0).idempotencyKey().isEmpty());
    }

    @Test
    void shouldReturnEmptyListForHeaderOnly(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("empty.csv");
        Files.writeString(csv, "coffeeType,price,currency,loyaltyCardId,idempotencyKey\n");

        List<CsvPaymentRecord> records = reader.read(csv);

        assertTrue(records.isEmpty());
    }

    @Test
    void shouldReturnEmptyListForEmptyFile(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("empty.csv");
        Files.writeString(csv, "");

        List<CsvPaymentRecord> records = reader.read(csv);

        assertTrue(records.isEmpty());
    }
}
