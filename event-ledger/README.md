# Event Ledger

A distributed financial transaction processing system built with **Java 17** and **Spring Boot 3**,
composed of two independent microservices that work together to receive, store, and process
financial events with full observability, resiliency, and distributed tracing.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Setup Instructions](#setup-instructions)
- [Running the Services](#running-the-services)
  - [Option 1: Docker Compose (Recommended)](#option-1-docker-compose-recommended)
  - [Option 2: Run Manually (Without Docker)](#option-2-run-manually-without-docker)
- [Running the Tests](#running-the-tests)
- [API Reference](#api-reference)
  - [Event Gateway API (Port 8080)](#event-gateway-api-port-8080)
  - [Account Service API (Port 8081)](#account-service-api-port-8081)
- [Sample Requests](#sample-requests)
- [Resiliency Pattern](#resiliency-pattern)
- [Distributed Tracing](#distributed-tracing)
- [Observability](#observability)
- [Key Design Decisions](#key-design-decisions)
- [Bonus Features](#bonus-features)

---

## Architecture Overview

```
                          ┌─────────────────────────┐
  Browser / Client ──────►│    Event Gateway API     │  :8080
                          │     (public-facing)      │
                          └────────────┬────────────┘
                                       │  REST + X-Trace-Id header
                                       │  Circuit Breaker / Retry / Timeout
                                       ▼
                          ┌─────────────────────────┐
                          │     Account Service      │  :8081
                          │       (internal)         │
                          └─────────────────────────┘
```

### Event Gateway (Port 8080)
- Public-facing entry point for all client requests
- Validates incoming event payloads
- Enforces **idempotency** — duplicate `eventId` returns original event, no side effects
- Stores event records in its **own H2 in-memory database**
- Calls Account Service to apply transactions
- Implements **Circuit Breaker + Retry + Timeout** on all Account Service calls
- Generates `traceId` and propagates it downstream via `X-Trace-Id` header
- Exposes `/events` and `/health` endpoints

### Account Service (Port 8081)
- Internal service — not exposed to external clients directly
- Manages account state: balances and transaction history
- Each service has its **own isolated H2 in-memory database**
- Handles **out-of-order events** correctly by recomputing balance from all transactions
- Enforces idempotency at the transaction level using `eventId` as a unique key
- Reads `X-Trace-Id` header and propagates it into structured logs
- Exposes `/accounts` and `/health` endpoints

---

## Project Structure

```
event-ledger/
├── event-gateway/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/eventledger/gateway/
│   │   │   │   ├── EventGatewayApplication.java
│   │   │   │   ├── client/
│   │   │   │   │   └── AccountServiceClient.java      # Resilience4j CB + Retry
│   │   │   │   ├── config/
│   │   │   │   │   ├── AppConfig.java                 # RestTemplate, ObjectMapper
│   │   │   │   │   ├── CircuitBreakerConfiguration.java
│   │   │   │   │   ├── EventMapper.java
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   └── MetricsConfig.java
│   │   │   │   ├── controller/
│   │   │   │   │   └── EventController.java
│   │   │   │   ├── filter/
│   │   │   │   │   └── TraceIdFilter.java             # Generates + propagates traceId
│   │   │   │   ├── health/
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── Event.java
│   │   │   │   │   ├── EventRequest.java
│   │   │   │   │   ├── EventResponse.java
│   │   │   │   │   └── EventStatus.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── EventRepository.java
│   │   │   │   └── service/
│   │   │   │       └── EventService.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── logback-spring.xml                 # JSON structured logging
│   │   └── test/
│   │       └── java/com/eventledger/gateway/
│   │           ├── EventIdempotencyTest.java
│   │           ├── EventValidationTest.java
│   │           ├── CircuitBreakerTest.java
│   │           ├── TraceIdPropagationTest.java
│   │           └── EventIntegrationTest.java
│   ├── Dockerfile
│   └── pom.xml
│
├── account-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/eventledger/account/
│   │   │   │   ├── AccountServiceApplication.java
│   │   │   │   ├── controller/
│   │   │   │   │   └── AccountController.java
│   │   │   │   ├── filter/
│   │   │   │   │   └── TraceIdFilter.java
│   │   │   │   ├── health/
│   │   │   │   │   └── HealthController.java
│   │   │   │   ├── model/
│   │   │   │   │   ├── Account.java
│   │   │   │   │   ├── Transaction.java
│   │   │   │   │   └── TransactionRequest.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── AccountRepository.java
│   │   │   │   │   └── TransactionRepository.java
│   │   │   │   └── service/
│   │   │   │       └── AccountService.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       └── logback-spring.xml
│   │   └── test/
│   │       └── java/com/eventledger/account/
│   │           ├── AccountBalanceTest.java
│   │           ├── OutOfOrderTest.java
│   │           └── TransactionIdempotencyTest.java
│   ├── Dockerfile
│   └── pom.xml
│
├── docker-compose.yml
└── README.md
```

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Database | H2 In-Memory (per service) |
| Resiliency | Resilience4j (Circuit Breaker + Retry + TimeLimiter) |
| Logging | Logback + Logstash JSON Encoder |
| Metrics | Micrometer + Prometheus |
| Build | Maven |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5 + Mockito + WireMock |

---

## Prerequisites

Ensure the following are installed on your machine:

| Tool | Version | Check |
|------|---------|-------|
| Java JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | 24+ | `docker -version` |
| Docker Compose | 2.0+ | `docker compose version` |

---

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/event-ledger.git
cd event-ledger
```

### 2. Build Both Services

```bash
# Build Event Gateway
cd event-gateway
mvn clean package -DskipTests
cd ..

# Build Account Service
cd account-service
mvn clean package -DskipTests
cd ..
```

---

## Running the Services

### Option 1: Docker Compose (Recommended)

Start both services with a single command from the project root:

```bash
docker compose up --build
```

To run in detached (background) mode:

```bash
docker compose up --build -d
```

To stop all services:

```bash
docker compose down
```

Verify both services are running:

```bash
curl http://localhost:8080/health   # Event Gateway
curl http://localhost:8081/health   # Account Service
```

---

### Option 2: Run Manually (Without Docker)

Open **two separate terminals**.

**Terminal 1 — Start Account Service first:**

```bash
cd account-service
mvn spring-boot:run
```

Wait until you see:
```
Started AccountServiceApplication on port 8081
```

**Terminal 2 — Start Event Gateway:**

```bash
cd event-gateway
mvn spring-boot:run
```

Wait until you see:
```
Started EventGatewayApplication on port 8080
```

> ⚠️ Always start Account Service **before** Event Gateway, so the circuit breaker
> does not trip immediately on startup.

---

## Running the Tests

### Run all tests (both services):

```bash
# Event Gateway tests
cd event-gateway
mvn test

# Account Service tests
cd account-service
mvn test
```

### Run a specific test class:

```bash
mvn test -Dtest=CircuitBreakerTest
mvn test -Dtest=EventIdempotencyTest
mvn test -Dtest=OutOfOrderTest
```

### Test Coverage Report:

```bash
mvn test jacoco:report
# Report available at: target/site/jacoco/index.html
```

### What the Tests Cover:

| Test Class | Coverage |
|-----------|---------|
| `EventIdempotencyTest` | Duplicate `eventId` returns 200 with original event |
| `EventValidationTest` | Missing fields, negative amount, invalid type → 400 |
| `CircuitBreakerTest` | Account Service down → 503, circuit opens, fallback fires |
| `TraceIdPropagationTest` | `X-Trace-Id` flows from Gateway to Account Service |
| `EventIntegrationTest` | Full Gateway → Account Service flow end-to-end |
| `OutOfOrderTest` | Events arriving out of order produce correct balance |
| `TransactionIdempotencyTest` | Same `eventId` at Account Service does not alter balance |
| `AccountBalanceTest` | CREDIT/DEBIT combinations compute correct net balance |

---

## API Reference

### Event Gateway API (Port 8080)

#### POST /events — Submit a transaction event

```
POST http://localhost:8080/events
Content-Type: application/json
```

Request Body:

```json
{
  "eventId": "evt-001",
  "accountId": "acct-123",
  "type": "CREDIT",
  "amount": 150.00,
  "currency": "USD",
  "eventTimestamp": "2026-05-15T14:02:11Z",
  "metadata": {
    "source": "mainframe-batch",
    "batchId": "B-9042"
  }
}
```

| Response Code | Meaning |
|--------------|---------|
| `201 Created` | New event accepted and processed |
| `200 OK` | Duplicate `eventId` — returns original event |
| `400 Bad Request` | Validation failed (missing fields, invalid type, amount ≤ 0) |
| `503 Service Unavailable` | Account Service unreachable (circuit open) |

---

#### GET /events/{id} — Get event by ID

```
GET http://localhost:8080/events/evt-001
```

| Response Code | Meaning |
|--------------|---------|
| `200 OK` | Event found |
| `404 Not Found` | Event does not exist |

> ✅ Works even when Account Service is unavailable.

---

#### GET /events?account={accountId} — List events for account

```
GET http://localhost:8080/events?account=acct-123
```

Returns events sorted by `eventTimestamp` ASC (chronological order).

> ✅ Works even when Account Service is unavailable.

---

#### GET /health — Gateway health check

```
GET http://localhost:8080/health
```

```json
{
  "service": "event-gateway",
  "status": "UP",
  "timestamp": "2026-05-15T10:00:00Z",
  "database": {
    "status": "UP",
    "type": "H2 In-Memory"
  }
}
```

---

### Account Service API (Port 8081)

> ⚠️ Internal service — called only by the Event Gateway. Not for direct client use.

#### POST /accounts/{accountId}/transactions

```
POST http://localhost:8081/accounts/acct-123/transactions
X-Trace-Id: <traceId-from-gateway>
Content-Type: application/json
```

#### GET /accounts/{accountId}/balance

```
GET http://localhost:8081/accounts/acct-123/balance
```

#### GET /accounts/{accountId}

```
GET http://localhost:8081/accounts/acct-123
```

#### GET /health

```
GET http://localhost:8081/health
```

---

## Sample Requests

### Submit a CREDIT event:

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 500.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T10:00:00Z"
  }'
```

### Submit a DEBIT event:

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-002",
    "accountId": "acct-123",
    "type": "DEBIT",
    "amount": 200.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T11:00:00Z"
  }'
```

### Submit duplicate event (idempotency check):

```bash
# Same evt-001 again — returns original, balance unchanged
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventId": "evt-001",
    "accountId": "acct-123",
    "type": "CREDIT",
    "amount": 500.00,
    "currency": "USD",
    "eventTimestamp": "2026-05-15T10:00:00Z"
  }'
```

### Get events for account (chronological order):

```bash
curl http://localhost:8080/events?account=acct-123
```

### Get account balance:

```bash
curl http://localhost:8081/accounts/acct-123/balance
```

Expected response (net balance = 500 - 200 = 300):

```json
{
  "accountId": "acct-123",
  "balance": 300.00,
  "creditCount": 1,
  "debitCount": 1,
  "timestamp": "2026-05-15T12:00:00Z"
}
```

---

## Resiliency Pattern

### Choice: Circuit Breaker + Retry + Timeout (Resilience4j)

The Event Gateway implements three complementary patterns on every call to the Account Service:

```
Client Request
     │
     ▼
┌─────────────┐     OPEN (tripped)     ┌──────────────────┐
│Circuit Breaker│ ──────────────────► │ Fallback (503)    │
│   CLOSED    │                        └──────────────────┘
└──────┬──────┘
       │ CLOSED (passing)
       ▼
┌─────────────┐     Timeout (>5s)      ┌──────────────────┐
│ TimeLimiter │ ──────────────────►   │ Retry with backoff│
└──────┬──────┘                        └──────────────────┘
       │
       ▼
┌─────────────┐     Max 3 attempts
│    Retry    │     500ms → 1s → 2s (exponential backoff)
└──────┬──────┘
       │
       ▼
  Account Service
```

### Why Circuit Breaker?

| Reason | Explanation |
|--------|------------|
| **Fail fast** | When Account Service is down, Gateway returns 503 immediately instead of hanging |
| **Prevents cascade failure** | Stops thread pool exhaustion on the Gateway side |
| **Self-healing** | Automatically retries Account Service after 10s cooldown |
| **Graceful degradation** | GET /events endpoints still work during Account Service outage |

### Circuit Breaker Configuration:

| Setting | Value |
|---------|-------|
| Sliding window | Last 5 calls |
| Failure rate threshold | 50% |
| Wait in OPEN state | 10 seconds |
| Probe calls in HALF-OPEN | 2 |
| Timeout per call | 5 seconds |
| Max retry attempts | 3 |
| Retry backoff | 500ms, 1s, 2s (exponential) |

---

## Distributed Tracing

Every request through the system carries a **Trace ID** that links logs across both services.

### Flow:

```
Client Request
     │
     ▼
Gateway TraceIdFilter
  → Generates UUID traceId (or reads X-Trace-Id if provided)
  → Puts traceId into MDC (appears in all log lines)
  → Sets X-Trace-Id response header
     │
     ▼ HTTP call with header: X-Trace-Id: <uuid>
     │
Account Service AccountController
  → Reads X-Trace-Id header
  → Puts traceId into MDC
  → All logs include the same traceId
```

### Sample correlated logs across both services:

**Gateway log:**
```json
{
  "service": "event-gateway",
  "traceId": "abc-123-xyz",
  "level": "INFO",
  "message": "Processing event eventId=evt-001 accountId=acct-123"
}
```

**Account Service log (same traceId):**
```json
{
  "service": "account-service",
  "traceId": "abc-123-xyz",
  "level": "INFO",
  "message": "Applying transaction accountId=acct-123 eventId=evt-001"
}
```

Search by `traceId` across both service logs to trace a full request end-to-end.

---

## Observability

### Structured JSON Logging

All logs are emitted as JSON with these fields:

| Field | Description |
|-------|------------|
| `timestamp` | ISO-8601 timestamp |
| `level` | INFO / DEBUG / WARN / ERROR |
| `service` | `event-gateway` or `account-service` |
| `traceId` | Unique request trace ID |
| `accountId` | Account being processed |
| `eventId` | Event being processed |
| `message` | Log message |

### Health Endpoints

```bash
GET http://localhost:8080/health   # Gateway
GET http://localhost:8081/health   # Account Service
```

### Metrics (Prometheus)

```bash
GET http://localhost:8080/actuator/prometheus
GET http://localhost:8081/actuator/prometheus
```

### Custom Metrics:

| Metric | Description |
|--------|------------|
| `events.received.total` | Total events submitted to Gateway |
| `events.duplicate.total` | Total duplicate events rejected |
| `events.failed.total` | Total events that failed processing |
| `account.service.errors.total` | Total Account Service call failures |

### H2 Console (Dev only):

```
http://localhost:8080/h2-console   # Gateway DB
http://localhost:8081/h2-console   # Account Service DB

JDBC URL : jdbc:h2:mem:gatewaydb   (or accountdb)
Username : sa
Password : (leave blank)
```

---

## Key Design Decisions

### 1. Out-of-Order Event Handling
Balance is **always recomputed** from all stored transactions using a JPQL SUM query,
rather than maintaining a running total. This means arrival order has zero impact on
correctness — a DEBIT arriving before its corresponding CREDIT always produces the right balance.

### 2. Idempotency at Both Layers
Both services independently check for duplicate `eventId` before processing.
The Gateway returns the original stored event. The Account Service skips balance
updates entirely. This double-check ensures correctness even under partial failures.

### 3. Service Isolation
Each service has its own H2 in-memory database with no shared state. The Gateway's
database stores event records. The Account Service's database stores accounts and
transactions. GET endpoints on the Gateway work independently of Account Service availability.

### 4. Graceful Degradation
When Account Service is down:
- `POST /events` → returns `503` immediately (circuit open), event saved as `FAILED`
- `GET /events/{id}` → still works (Gateway DB only)
- `GET /events?account=` → still works (Gateway DB only)
- Balance queries → returns clear `503` with message

---

## Bonus Features

- [x] Prometheus metrics endpoint (`/actuator/prometheus`)
- [x] Retry with exponential backoff (500ms → 1s → 2s)
- [x] H2 Console for local database inspection
- [x] Custom metrics (received, duplicate, failed, errors)
- [x] Structured JSON logging with full MDC context
- [ ] OpenTelemetry Collector + Jaeger (not implemented)
- [ ] Rate limiting on Gateway (not implemented)
- [ ] Async fallback queue (not implemented)

---

## Author

**Dileepsingh B. Rathaur**
Senior Java Backend Engineer

- 📧 rathoredilip38@gmail.com
- 🐙 [github.com/RathoreDilip](https://github.com/RathoreDilip)
