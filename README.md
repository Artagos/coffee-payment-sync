# Coffee Place Payment Sync

Automation tool that accepts coffee-shop payments from a CSV file and reliably sends them to the StarHarbour Payments Service via its REST API.

## How it works

1. You collect payments in a CSV file format: `coffeeType,price,currency,loyaltyCardId,idempotencyKey`
2. The tool reads each row and POSTs it to `POST /api/v1/payments` with `Store-Id` and `Idempotency-Key` headers
3. If a request fails, it retries up to 3 times with exponential backoff
4. A summary report is printed showing which payments succeeded or failed

## CSV format

```csv
coffeeType,price,currency,loyaltyCardId,idempotencyKey
LATTE,3.50,EUR,card-001,order-001
ESPRESSO,2.00,USD,card-002,order-002
```

Fields: `coffeeType` (required), `price` (required), `currency` (required), `loyaltyCardId` (optional), `idempotencyKey` (optional — auto-generated if omitted).

## Build

```bash
mvn clean package
```

## Run

Start the StarHarbour Payments Service first, then:

```bash
java -jar target/coffee-payment-sync-1.0.0.jar sample-payments.csv
```

## Configuration

Edit `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `payment.api.base-url` | `http://localhost:8080` | StarHarbour service URL |
| `payment.api.store-id` | `coffee-place-downtown` | Store identifier sent in `Store-Id` header |
| `sync.max-retries` | `3` | Number of retry attempts on failure |
| `sync.retry-backoff-ms` | `1000` | Delay between retries in milliseconds |
