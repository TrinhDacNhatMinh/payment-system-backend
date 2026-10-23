# Payment System

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Test Status](https://img.shields.io/badge/tests-not%20verified-lightgrey.svg)](#testing)

A monolithic backend service (REST API) built with Spring Boot for a simple e-wallet system. It handles user registration, wallet balance management, internal fund transfers with concurrency control, and integrates with the VNPay sandbox for depositing funds.

## Features

- **Account Management**: Register, login, refresh tokens, and logout. One active refresh session per user; previous refresh tokens are revoked upon new login.
- **Wallet & Balance**: Automatically provisions a wallet for new users. Users can retrieve their current balance.
- **VNPay Deposit**: Create a deposit order returning a VNPay payment URL. A webhook endpoint processes the VNPay IPN callback to credit the wallet exactly once (idempotent).
- **Fund Transfer**: Secure internal transfer between users. Enforces self-transfer restrictions, sufficient balance checks, and handles concurrent requests using Optimistic Locking.
- **Transaction History**: Track all deposit, transfer_in, and transfer_out activities. Provides a paginated history endpoint with filtering by type, status, and date.

## Tech Stack

| Layer            | Technology                                  |
|------------------|---------------------------------------------|
| Language         | Java 17                                     |
| Framework        | Spring Boot 3.4.1                           |
| Build Tool       | Maven                                       |
| Database         | PostgreSQL 16                               |
| ORM              | Spring Data JPA (Hibernate)                 |
| Security         | Spring Security + jjwt (0.12.6)             |
| Payment Gateway  | VNPay Sandbox                               |
| Documentation    | springdoc-openapi-starter-webmvc-ui (2.8.4) |
| Containerization | Docker, Docker Compose                      |
| Testing          | JUnit 5, Mockito                            |

## Getting Started

### Prerequisites

- Java 17
- Docker & Docker Compose
- Maven

### Installation

```bash
git clone https://github.com/TrinhDacNhatMinh/payment-system-backend.git

cd payment-system-backend

cp src/main/resources/application.yaml.example src/main/resources/application.yaml

cp docker-compose.yml.example docker-compose.yml

# Fill in application.yaml and docker-compose.yml before starting.
docker compose up -d --build
```

### Configuration

- Copy `src/main/resources/application.yaml.example` to `src/main/resources/application.yaml`, then configure PostgreSQL, JWT, and VNPay. Use a JWT secret of at least 32 UTF-8 bytes.
- Copy `docker-compose.yml.example` to `docker-compose.yml`, then configure the same database name, username, and password. Docker sets the backend database URL to `db:5432`.

### Running

```bash
# Run the application locally with the project root as the working directory
mvn spring-boot:run

# Build and run the backend and PostgreSQL with Docker
docker compose up -d --build

# Run tests
mvn test
```

## API Documentation

**Swagger UI:** http://localhost:8080/swagger-ui.html

| Method | Endpoint                          | Description                                | Auth                                   |
|--------|-----------------------------------|--------------------------------------------|----------------------------------------|
| POST   | `/api/v1/auth/register`           | Register a new user                        | CSRF + Origin                          |
| POST   | `/api/v1/auth/login`              | Return access token and set refresh cookie | CSRF + Origin                          |
| POST   | `/api/v1/auth/refresh`            | Rotate tokens using refresh cookie         | CSRF + Origin + refresh cookie         |
| POST   | `/api/v1/auth/logout`             | End session and clear cookies; repeatable  | CSRF + Origin; optional refresh cookie |
| GET    | `/api/v1/wallet/me`               | Get current user's wallet balance          | USER                                   |
| POST   | `/api/v1/payment/deposit`         | Create a VNPay deposit URL                 | USER                                   |
| GET    | `/api/v1/payment/vnpay-webhook`   | VNPay IPN Webhook callback                 | None                                   |
| GET    | `/api/v1/payment/vnpay-return`    | VNPay Return URL callback                  | None                                   |
| POST   | `/api/v1/transfer`                | Transfer funds to another user             | USER                                   |
| GET    | `/api/v1/transactions`            | View personal transaction history          | USER                                   |

## Project Structure

```text
src/main/java/com/minh/paymentsystem/
├── auth/
├── common/
├── config/
├── payment/
├── transaction/
├── transfer/
├── user/
└── wallet/
```

---

## Architecture & Design Docs

### Overview

The system follows a **Monolith** architecture organized by **package-by-domain** principles. Each domain (auth, wallet, payment, etc.) encapsulates its own Controller, Service, Repository, and DTOs. This reduces cross-domain coupling. A `common` package serves as the shared kernel for global exception handling and base response structures.

### Service Dependency

The application enforces boundaries by ensuring Controllers only call their respective Services, and Services do not directly manipulate the Repositories of other domains.

- `AuthController` -> `AuthService` -> `UserService`, `RefreshTokenService`, `WalletService` (to create wallet)
- `TransferController` -> `TransferService` -> `UserService` (to fetch user info), `WalletService` (for debit/credit), `TransactionService` (to log transactions)
- `PaymentController` -> `PaymentService` -> `UserService` (to fetch user info), `PaymentGatewayService` (interface abstraction), `WalletService`, `TransactionService`

### Database Schema (ERD)

```mermaid
erDiagram
    USERS ||--o{ REFRESH_TOKENS : "has"
    USERS ||--|| WALLETS : "has"
    USERS ||--o{ PAYMENT_ORDERS : "creates"
    WALLETS ||--o{ TRANSACTIONS : "has"
    TRANSACTIONS ||--o| TRANSACTIONS : "related_to"

    USERS {
        bigint id PK
        varchar email UK
        varchar password
        varchar full_name
        varchar phone
        varchar role
        varchar status
        datetime created_at
        datetime updated_at
    }

    REFRESH_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token UK
        datetime expires_at
        boolean revoked
    }

    WALLETS {
        bigint id PK
        bigint user_id FK
        decimal balance
        varchar currency
        bigint version
        datetime updated_at
    }

    TRANSACTIONS {
        bigint id PK
        bigint wallet_id FK
        varchar type
        decimal amount
        varchar status
        varchar reference_code UK
        bigint related_transaction_id FK
        varchar description
        datetime created_at
        datetime updated_at
    }

    PAYMENT_ORDERS {
        bigint id PK
        bigint user_id FK
        varchar order_id UK
        decimal amount
        varchar status
        varchar vnp_transaction_no
        datetime created_at
        datetime paid_at
    }
```

### Sequence Diagram — Deposit (VNPay)

```mermaid
<!-- sequenceDiagram
    actor Client
    participant Backend
    participant Database
    participant VNPay

    Client->>Backend: Create deposit with amount and Idempotency-Key<br/>/api/v1/payment/deposit
    Backend->>Database: Find existing deposit request
    Database-->>Backend: Existing order or no match
    alt Existing request
        Backend-->>Client: Return the same order and original link expiry
    else New request
        Backend->>Database: Save pending order and signed payment URL
        Database-->>Backend: Order saved
        Backend-->>Client: Return order and payment URL valid for 15 minutes
    end
    Client->>VNPay: Open payment URL and complete payment

    par Browser return
        VNPay-->>Client: Redirect to backend return URL<br/>/api/v1/payment/vnpay-return
        Client->>Backend: Send payment return parameters<br/>/api/v1/payment/vnpay-return
        Backend->>Database: Read order to verify return
        Database-->>Backend: Order details
        Backend-->>Client: Redirect to frontend result page (HTTP 303)
    and Payment notification (IPN)
        VNPay->>Backend: Notify payment result<br/>/api/v1/payment/vnpay-webhook
        Backend->>Database: Save payment result, transaction and any wallet changes together
        Database-->>Backend: Committed result
        Backend-->>VNPay: Acknowledge notification
    end
    Note over Client,VNPay: Return and IPN may arrive in either order
    Client->>Backend: Look up deposit by order ID or request key<br/>/api/v1/payment/orders/{orderId}<br/>or /api/v1/payment/deposit/requests/{idempotencyKey}
    Backend->>Database: Read order owned by the authenticated user
    Database-->>Backend: Current payment status
    Backend-->>Client: Return order status
    Note over Client,Backend: Return alone does not confirm payment or credit the wallet -->
```

### Sequence Diagram — Transfer

```mermaid
sequenceDiagram
    actor Client
    participant Backend
    participant Database

    Client->>Backend: Request transfer with recipient, amount and Idempotency-Key<br/>/api/v1/transfer
    Backend->>Database: Register or read transfer request
    Database-->>Backend: Request and any stored outcome
    alt Request already completed
        Backend-->>Client: Return stored receipt or rejection
    else Request requires processing
        Backend->>Database: Read sender and recipient wallets
        Database-->>Backend: Wallet details
        Backend->>Database: Update balances and save transactions and receipt together
        Database-->>Backend: Committed transfer outcome
        Backend-->>Client: Return transfer result
    end
    Note over Backend,Database: Failed processing does not commit partial money changes
    opt Client needs to check an uncertain outcome
        Client->>Backend: Look up transfer by the same request key<br/>/api/v1/transfer/requests/{idempotencyKey}
        Backend->>Database: Read request owned by the authenticated user
        Database-->>Backend: Current request status and stored outcome
        Backend-->>Client: Return status without executing another transfer
    end
```

### Sequence Diagram — Authentication & Sessions

```mermaid
sequenceDiagram
    actor Client
    participant Backend
    participant Database

    Client->>Backend: Request CSRF token<br/>/api/v1/auth/csrf
    Backend-->>Client: CSRF token and matching cookie
    Note over Client,Backend: Authentication POST requests require an allowed Origin and CSRF token/cookie
    opt Register a new account
        Client->>Backend: Submit registration details<br/>/api/v1/auth/register
        Backend->>Database: Save user and wallet together
        Database-->>Backend: Account created
        Backend-->>Client: Return registered user
    end

    Client->>Backend: Submit email and password to log in<br/>/api/v1/auth/login
    Backend->>Database: Read account
    Database-->>Backend: Account details
    Backend->>Database: Replace previous refresh session after successful authentication
    Database-->>Backend: Session saved
    Backend-->>Client: Access token, user details and HttpOnly refresh cookie
    Note over Client,Backend: Fetch a new CSRF token after successful login

    opt Refresh access token
        Client->>Backend: Request refresh using refresh cookie<br/>/api/v1/auth/refresh
        Backend->>Database: Read refresh session and account
        Database-->>Backend: Session and account details
        Backend->>Database: Replace validated refresh session
        Database-->>Backend: New session saved
        Backend-->>Client: New access token, user details and refresh cookie
    end
    Client->>Backend: Call protected API with Bearer access token<br/>e.g. /api/v1/wallet/me
    Backend->>Database: Read current user and account status
    Database-->>Backend: User details
    Backend-->>Client: Return API response if authorized

    opt Log out
        Client->>Backend: Request logout using refresh cookie<br/>/api/v1/auth/logout
        Backend->>Database: Revoke applicable refresh session
        Database-->>Backend: Session ended or already ended
        Backend-->>Client: Return success and clear refresh/CSRF cookies
    end
```

### Key Technical Highlights

- **Optimistic Locking**: Implemented via `@Version` on the `Wallet` entity to detect concurrent wallet updates and roll back conflicting transactions.
- **Idempotency Control**: The VNPay IPN webhook uses the `order_id` to ensure that even if VNPay sends multiple callbacks, the wallet is credited exactly once.
- **Interface-based Gateway**: The `PaymentGatewayService` is defined as an interface, making the `PaymentService` completely decoupled from VNPay. This allows seamlessly integrating or swapping in any other third-party payment providers in the future.
- **Single-session Refresh**: Login, refresh, and logout coordinate through a user-row lock; new login revokes previous refresh tokens.

## Testing

`mvn test`

Tested components:

- `AuthServiceImplTest` (register, login, refresh, logout)
- `PaymentServiceImplTest` (deposit, webhook callback handling)
- `TransactionServiceImplTest` (create, log, filter)
- `TransferServiceImplTest` (transfer happy/error paths)
- `TransferServiceConcurrencyTest` (simulated concurrent transfers)
- `UserServiceImplTest` (session locking and user lookup)
- `WalletServiceImplTest` (credit, debit limits)
