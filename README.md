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
- **Admin Management**: Dedicated admin endpoints to fetch all system transactions and lock/unlock user accounts.

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
| GET    | `/api/v1/admin/transactions`      | View all system transactions               | ADMIN                                  |
| PATCH  | `/api/v1/admin/users/{id}/lock`   | Lock a user account                        | ADMIN                                  |
| PATCH  | `/api/v1/admin/users/{id}/unlock` | Unlock a user account                      | ADMIN                                  |

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
sequenceDiagram
    actor User
    participant App as PaymentSystem
    participant VNPay

    User->>App: POST /api/v1/payment/deposit (amount)
    App->>App: Validate amount & create PaymentOrder (PENDING)
    App-->>User: Return VNPay payment URL
    User->>VNPay: Proceed to pay on VNPay sandbox
    VNPay-->>User: Redirect to Return URL
    VNPay->>App: GET IPN Webhook (vnpay-webhook)
    App->>App: Verify checksum
    alt Invalid Checksum
        App-->>VNPay: Return 97 Invalid Checksum
    else Valid Checksum
        App->>App: Check Idempotency (Order must be PENDING)
        alt Payment Success (00)
            App->>App: Update Order to SUCCESS
            App->>App: Credit Wallet & Create Transaction (DEPOSIT)
        else Payment Failed
            App->>App: Update Order to FAILED
            App->>App: Create Transaction (FAILED)
        end
        App-->>VNPay: Return 00 Confirm Success
    end
```

### Sequence Diagram — Transfer

```mermaid
sequenceDiagram
    actor Sender
    participant App as TransferService

    Sender->>App: POST /api/v1/transfer (toEmail, amount)
    App->>App: Validate (amount > 0, sender != receiver, recipient ACTIVE)
    App->>App: WalletService.debit(senderWalletId, amount) (Optimistic Locking)
    alt OptimisticLockingFailureException (concurrent modification)
        App-->>Sender: 409 CONCURRENT_MODIFICATION — please retry
    else success
        App->>App: WalletService.credit(recipientWalletId, amount)
        App->>App: Create Transaction TRANSFER_OUT for Sender
        App->>App: Create Transaction TRANSFER_IN for Receiver
        App->>App: Link both transactions (related_transaction_id)
        App-->>Sender: Return Success
    end
```

### Sequence Diagram — JWT Authentication Flow

```mermaid
sequenceDiagram
    actor Client
    participant Controller as AuthController / AuthService
    participant Provider as JwtTokenProvider
    participant Filter as JwtAuthenticationFilter
    participant Context as SecurityContext

    Client->>Controller: POST /api/v1/auth/login (Origin + CSRF token)
    Controller->>Provider: Generate tokens after credential validation
    Provider-->>Controller: Tokens generated
    Controller-->>Client: Access token and user JSON + HttpOnly refresh cookie
    Client->>Filter: Request API with "Bearer <AccessToken>"
    Filter->>Provider: Validate Access Token
    Provider-->>Filter: Token valid (Claims)
    Filter->>Context: Set Authentication (UsernamePasswordAuthenticationToken)
    Context-->>Client: Proceed to Controller & Return Data
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
- `UserServiceImplTest` (lock, unlock)
- `WalletServiceImplTest` (credit, debit limits)
