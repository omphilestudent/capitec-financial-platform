# Capitec Financial Intelligence Platform

A microservices banking platform: customers, accounts and transactions are owned by separate Spring Boot services, each with its own PostgreSQL database. Services talk to each other over REST for synchronous checks and over Apache Kafka for events. A React frontend reads and writes everything through an API gateway.

Everything described below is implemented and was verified against the running platform. Capabilities without a service in this repository (cases, fraud detection, notifications, statements) are shown as not implemented in the UI; no sample data is displayed for them.

## Architecture

```text
                 React frontend (Vite dev server, :5173)
                              |  /api/v1/*
                              v
                    API Gateway  (:8000, Spring Cloud Gateway)
                 routes by path, reports service health
            +---------------+----------------+----------------+
            |               |                |
            v               v                v
   Customer Service   Account Service   Transaction Service
     (:8081)            (:8082)           (:8083)
        |                  |                 |
        |  REST: account-service checks    REST: transaction-service
        |  customer existence (fallback)   checks account existence
        |                  ^                 |
        v                  |                 v
   customer_service_db  account_service_db  transaction_service_db
   (PostgreSQL, :5433, one database per service)

   Kafka (localhost:9092, KRaft)
   customer-service --CustomerCreated-->  customer.events  --> account-service
   account-service  --AccountOpened---->  account.events
   transaction-service --TransactionCreated--> transaction-events
   failed customer.events messages --> customer.events-dlt
```

Each publishing service writes its event to a **transactional outbox** table in its own database, in the same transaction as the business change. A relay publishes pending outbox rows to Kafka and retries failures. An event is therefore never published for a rolled-back change, and a committed change is not lost if Kafka is briefly unavailable.

## Services

| Service | Port | Database | Responsibility | Publishes | Consumes |
|---|---|---|---|---|---|
| api-gateway | 8000 | none | Single entry point for the frontend; routes `/api/v1/customers`, `/accounts`, `/transactions`; reports downstream health at `/api/v1/services/health` | – | – |
| customer-service | 8081 | `customer_service_db` | Customer registration and lookup | `customer.events` | – |
| account-service | 8082 | `account_service_db` | Accounts per customer; keeps a local copy of known customers | `account.events` | `customer.events` |
| transaction-service | 8083 | `transaction_service_db` | Records credit and debit transactions against an account | `transaction-events` | – |
| frontend | 5173 | none | React console: Home, Customers, Transactions | – | – |

Database configuration is set per service in `application.properties` and can be overridden with `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. The default URL uses `localhost:5433`, user `app_user`. `transaction-service` has no default password and must be given `DB_PASSWORD`.

Schema changes are managed by Flyway; `spring.jpa.hibernate.ddl-auto=validate` makes Hibernate check the entities against the migrated schema at startup.

## Kafka

| Topic | Key | Producer | Consumer group |
|---|---|---|---|
| `customer.events` | customer ID | customer-service | `account-service` |
| `account.events` | account ID | account-service | – |
| `transaction-events` | transaction ID | transaction-service | – |
| `customer.events-dlt` | – | account-service (dead letters) | – |

Producers send JSON text from their outbox. Consumers apply the following rules:

- A failed record is retried three times, one second apart, then published to `customer.events-dlt`.
- A malformed payload is reported as an error and dead-lettered; it does not stop the listener.
- Customer references are stored by customer ID, so a redelivered event updates the same row and never creates a duplicate.

### Event schemas

Every event carries a unique `eventId` (UUID) and a timestamp.

**CustomerCreated** (`customer.events`)

```json
{
  "eventId": "UUID",
  "customerId": "UUID",
  "firstName": "string",
  "lastName": "string",
  "email": "string",
  "clientType": "INDIVIDUAL | BUSINESS",
  "occurredAt": "2026-11-18T17:52:10.123456Z"
}
```

**AccountOpened** (`account.events`)

```json
{
  "eventId": "UUID",
  "accountId": "UUID",
  "customerId": "UUID",
  "accountNumber": "string",
  "accountType": "SAVINGS | CHEQUE | CREDIT",
  "occurredAt": "ISO-8601 instant"
}
```

**TransactionCreated** (`transaction-events`)

```json
{
  "eventId": "UUID",
  "transactionId": "UUID",
  "accountId": "UUID",
  "transactionReference": "TXN-…",
  "type": "CREDIT | DEBIT",
  "amount": 1250.50,
  "currency": "ZAR",
  "status": "PENDING",
  "createdAt": "2026-11-18T17:57:45.838074"
}
```

Inspect messages with the Kafka CLI:

```bash
docker exec capitec-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic transaction-events \
  --from-beginning --property print.key=true
```

## API

All endpoints are served through the gateway at `http://localhost:8000`. Errors use one JSON shape with `status`, `error` and `message`; validation errors add `fieldErrors`.

| Method | Path | Purpose | Success | Errors |
|---|---|---|---|---|
| POST | `/api/v1/customers` | Create a customer | 201 | 400 invalid, 409 duplicate email |
| GET | `/api/v1/customers` | List customers | 200 | – |
| GET | `/api/v1/customers/{id}` | Get a customer | 200 | 404 |
| POST | `/api/v1/accounts` | Open an account `{customerId, accountType}` | 201 | 400, 404 unknown customer |
| GET | `/api/v1/accounts/{id}` | Get an account | 200 | 404 |
| GET | `/api/v1/accounts/customer/{customerId}` | List a customer's accounts | 200 | 404 unknown customer |
| POST | `/api/v1/transactions` | Record a transaction `{accountId, type, amount, currency, description?}` | 201 | 400, 404 unknown account, 503 account-service unavailable |
| GET | `/api/v1/transactions` | Most recent 100 transactions | 200 | – |
| GET | `/api/v1/transactions/{id}` | Get a transaction | 200 | 400 malformed ID, 404 |
| GET | `/api/v1/transactions/account/{accountId}` | An account's transactions | 200 | 404 unknown account |
| GET | `/api/v1/services/health` | Live health of the three services | 200 | – |

A transaction is created only after account-service confirms the account exists. If account-service cannot be reached, the request fails with 503 and nothing is written.

## Frontend

React and Vite. The browser calls `/api/v1/...`; the Vite dev server forwards those requests to the gateway (`frontend/vite.config.js`), so no URLs are hard-coded in components. All data comes from `frontend/src/services/*.js`, which wrap the shared axios client in `services/api.js`.

- **Home:** customer count, recent transaction count, services running, live service health, recent transactions, and a list of capabilities that are not implemented.
- **Customers:** list, create, detail, and each customer's accounts (open an account, view an account).
- **Transactions:** recent transactions, detail, search, and record a new transaction (choose customer, then account).
- **Cases:** states that no service provides cases.

Loading, empty and error states are shown per panel. A failed request shows its error; it does not fall back to other data.

## Running the platform

Prerequisites: Java 21, Node.js 20+, Docker Desktop.

**1. Infrastructure**

```bash
docker compose up -d postgres                              # PostgreSQL on :5433
docker compose -f infrastructure/kafka/docker-compose.yml up -d   # Kafka on :9092
```

Create the three service databases once (existing databases are left untouched):

```bash
for db in customer_service_db account_service_db transaction_service_db; do
  docker exec -e PGPASSWORD="$DB_PASSWORD" capitec-postgres \
    psql -U app_user -d financial_platform -tc "select 1 from pg_database where datname='$db'" | grep -q 1 \
    || docker exec -e PGPASSWORD="$DB_PASSWORD" capitec-postgres \
       psql -U app_user -d financial_platform -c "create database $db"
done
```

**2. Services** (one terminal each; Flyway creates the tables on first start)

```bash
cd customer-service    && ./mvnw spring-boot:run
cd account-service     && CUSTOMER_SERVICE_URL=http://localhost:8081 ./mvnw spring-boot:run
cd transaction-service && DB_PASSWORD="$DB_PASSWORD" ACCOUNT_SERVICE_URL=http://localhost:8082 ./mvnw spring-boot:run
cd api-gateway         && ./mvnw spring-boot:run
```

Health checks: `http://localhost:8081/actuator/health`, `:8082`, `:8083`, and `http://localhost:8000/api/v1/services/health`.

**3. Frontend**

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
npm run build        # production build to frontend/dist
```

If Maven cannot download dependencies because a corporate proxy presents its own certificate, point Maven's JVM at your trust store for that run:

```bash
MAVEN_OPTS="-Djavax.net.ssl.trustStore=$HOME/.m2/cacerts-zscaler -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD}" ./mvnw spring-boot:run
```

## Testing

Unit tests (no running infrastructure needed):

```bash
for s in customer-service account-service transaction-service api-gateway; do
  (cd $s && ./mvnw test)
done
```

End-to-end verification against the running platform, PostgreSQL and Kafka. It creates real records through the gateway and checks the database rows, the published events (read with the Kafka CLI) and the failure responses:

```bash
node scripts/e2e/verify-platform.mjs
```

The script covers: customer, account and transaction creation; outbox rows marked `PUBLISHED`; the exact event contents on each topic; a duplicate customer event that must not create a second reference; 404, 400 and 409 responses that must create no rows; and a malformed message that must reach `customer.events-dlt`. It exits non-zero if any check fails.

Outage behaviour (stopping account-service and confirming transactions return 503 without writing a row) was checked manually; it is not in the script because it stops a running service.

### Fraud demonstration

Runs one suspicious transaction for the customer Omphile Mohlala through the real workflow: transaction-service, the outbox and Kafka, the fraud model, the fraud alert, the case, and the notification. Needs the platform running (`scripts/dev/bootstrap.sh`).

```bash
scripts/dev/seed-fraud-demo.sh                       # defaults: 60000 ZAR, 60 s per wait step
scripts/dev/seed-fraud-demo.sh --amount 75000 --timeout 120
```

- **Customer and account:** the existing customer named Omphile Mohlala is reused and never modified. A customer is created only if none exists, and an active savings account is opened only if the customer has none. Repeat runs do not create duplicate customers or accounts.
- **The transaction:** a DEBIT in ZAR of 60,000 with the description `Fraud demo DEMO-<timestamp>-<hex>: high-value purchase`. The amount is checked against the `HIGH_AMOUNT` threshold that the fraud service reports at `GET /api/v1/fraud/rules` (currently 50,000), and the command refuses an amount the rule would not flag. The description contains no "EFT", so `LARGE_EFT` does not fire. The `VELOCITY` rule can also fire if the account has more than five transactions in 10 minutes (for example after several runs). The score and rules printed are the ones the service recorded.
- **What is verified:** the outbox row is `PUBLISHED`; `processed_transactions` shows `DETECTED`; the alert exists; the alert and case reference each other and the same transaction, customer and account; and a `CASE_CREATED` notification exists (reported as a warning if it does not, not a failure).
- **Exit codes:** `0` complete, `1` a step failed or timed out, `2` a service is unreachable or the configuration is wrong. `DB_PASSWORD` is read from the environment or `.env` and is never printed.

Each run adds one transaction, one alert and one case. To close the demo cases, use the API rather than deleting rows: `PATCH /api/v1/cases/{id}` to `IN_PROGRESS`, then `RESOLVED` with `resolutionDetails`, and `PATCH /api/v1/fraud/{id}/status` for the alert. Demo records are easy to find by the `Fraud demo DEMO-` description and the `FRD-`/`CASE-` numbers in the run output.

Unit tests for the decision logic (no infrastructure needed): `node --test scripts/dev/seed-fraud-demo.test.mjs`

## Known limitations

- **Cases, fraud detection, notifications and statements** have no service in this repository and are not implemented.
- **Accounts cannot be edited or closed.** account-service exposes only create and read endpoints. The Edit and Remove buttons on the account page are disabled for this reason.
- **Transactions stay `PENDING`.** Nothing processes them yet, and balances are not updated.
- **Event delivery is at-least-once.** Consumers handle duplicates by key, not by a processed-events table. The outbox relay runs in each service; running two instances of one service would publish each event twice until the relay is made multi-instance safe.
- **Failed producer events are kept, not retried forever.** After 10 failed attempts an outbox row is marked `FAILED` and stays in the table for investigation; there is no automatic dead-letter topic for producers.
- **No authentication or authorisation.** The gateway and services accept any request.
- **Services run from Maven, not containers.** Only PostgreSQL and Kafka run in Docker. The services need the explicit local URLs shown above; the default `localhost` database URL did not connect reliably from the Spring processes during development, while `127.0.0.1` did.
- **The monolith in `backend/`** is no longer used by the frontend. Its customer and account data are separate from the services.
- **The UI was not checked in a browser** during this work. The API paths behind every screen were exercised, and the production build succeeds.

## Project structure

```text
api-gateway/           Spring Cloud Gateway: routes and service health
customer-service/      Customer microservice (Spring Boot, JPA, Flyway, Kafka producer)
account-service/       Account microservice (Spring Boot, JPA, Flyway, Kafka producer and consumer)
transaction-service/   Transaction microservice (Spring Boot, JPA, Flyway, Kafka producer)
backend/               Earlier monolith, not used by the frontend
frontend/              React + Vite console
infrastructure/kafka/  Kafka KRaft docker-compose
scripts/e2e/           End-to-end verification script
docker-compose.yml     PostgreSQL
```

## Author

**Omphile Mohlala**

Full Stack Software Engineering Project

Technologies: Java 21 | Spring Boot 4 | Spring Cloud Gateway | React | PostgreSQL | Apache Kafka | Docker
