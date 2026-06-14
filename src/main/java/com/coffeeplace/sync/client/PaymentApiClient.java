package com.coffeeplace.sync.client;

import com.coffeeplace.sync.csv.CsvPaymentRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class PaymentApiClient {

    private final String baseUrl;
    private final String storeId;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PaymentApiClient(
            @Value("${payment.api.base-url}") String baseUrl,
            @Value("${payment.api.store-id}") String storeId,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.storeId = storeId;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public record SendResult(int statusCode, PaymentApiResponse body, String errorMessage) {
        public boolean isSuccess() {
            return statusCode == 201 || statusCode == 200;
        }
    }

    public SendResult send(CsvPaymentRecord record) {
        try {
            String json = objectMapper.writeValueAsString(new PaymentRequestBody(
                    record.coffeeType(), record.price(), record.currency(), record.loyaltyCardId()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/payments"))
                    .header("Content-Type", "application/json")
                    .header("Store-Id", storeId)
                    .header("Idempotency-Key", record.idempotencyKey())
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            PaymentApiResponse body = null;
            if (!response.body().isEmpty()) {
                body = objectMapper.readValue(response.body(), PaymentApiResponse.class);
            }

            return new SendResult(response.statusCode(), body, null);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = e.toString();
            }
            return new SendResult(0, null, msg);
        }
    }
}
