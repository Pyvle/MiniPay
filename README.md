# MiniPay

MiniPay is a payment system simulator.

## Goal

The goal of this project is to build a clear and practical payment flow with users, wallets, transactions, roles, database relations, REST API, and a simple UI.

## Tech Stack

Backend:
- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Bean Validation

Frontend:
- React later, or simple server-rendered pages first

Tools:
- Gradle
- Postman
- Swagger / OpenAPI later
- Docker later

## Initial Scope

The first version focuses on the core payment flow:

- users
- wallets
- deposits
- transfers
- transaction history

## Later Scope

- roles and permissions
- authentication
- admin panel
- merchant accounts
- invoices
- refunds
- audit logs
- UI

## Wallet request idempotency

Deposit, withdrawal, and transfer endpoints require an `Idempotency-Key` header
containing a UUID. Use a new key for each new intended operation.

- Repeating a successful request with the same key and parameters returns its
  original HTTP status and JSON body without moving money again. The response
  describes the original transaction, even if later operations changed the wallet.
- New successful operations return HTTP `200` and a `TransactionResponse` with
  `id`, `fromWalletId`, `toWalletId`, `amount`, `type`, `status`, and `createdAt`.
  Deposits have no sender; withdrawals have no recipient. Balances are obtained
  through the wallet endpoint. The transaction can be read at `GET /api/transactions/{id}`.
- Keys saved before this response format changed replay their original stored
  JSON (including the old wallet format) and status unchanged. They are not
  deleted, converted, or executed again.
- Reusing a stored key with a different operation type, wallet, recipient, or
  amount returns `409 Conflict`. Numerically equal amounts such as `20.0` and
  `20.00` are treated as equal. Keys are shared across the three operation types.
- Reserving the key, updating balances, recording the financial operation, and
  saving the response happen in one database transaction.
- Failed attempts (including insufficient funds, balance limits, and lock
  timeouts) roll back all those changes. No new key or financial operation is
  retained. After resolving the cause, the same request can be retried with the
  same key. A conflicting retry does not remove the original successful record.

### Status meanings

- `IdempotencyStatus.PROCESSING`: an intermediate state inside the uncommitted
  transaction, not a separately committed background job.
- `IdempotencyStatus.COMPLETED`: the successful operation and its response have
  been saved together.
- `TransactionStatus.SUCCESS`: the status of a persisted financial operation.
- `TransactionStatus.CREATED` and `FAILED` remain in the enum but are not used by
  the current synchronous flow. Failed attempts are not recorded in a separate
  transaction; durable failure auditing is outside the current scope.

## Transaction history API

`GET /api/wallets/{id}/transactions` returns a public page object with exactly
`content`, `page`, `size`, `totalElements`, and `totalPages`. `content` contains
transaction responses; totals count only records matching all supplied filters.

- Defaults: `page=0`, `size=20`. Pages are zero-based; `page >= 0` and
  `1 <= size <= 100` are required. Invalid parameters return `400` in `ErrorResponse`
  format; excessive sizes are rejected rather than silently reduced.
- Ordering is fixed: `createdAt DESC`, then `id DESC`.
- Optional `type` and `status` use the enum names, such as `TRANSFER` and `SUCCESS`.
  Unknown values return `400`. Missing or empty filters (for example `?type=`)
  apply no restriction. This also applies to empty `from` and `to`.
- Optional `from` and `to` accept ISO local date-times, for example
  `2026-09-28T12:00:00`, in the same local time convention as stored `createdAt`.
  No time-zone conversion is performed. The interval is `[from, to)`:
  `from` is inclusive, `to` exclusive. Equal bounds return an empty page;
  `from > to` and malformed dates return `400`.
- An existing wallet with no matches, or a page beyond the result, returns `200`
  with empty `content`. A missing wallet returns `404` for valid parameters.
  Page bounds are validated before wallet lookup.

## Development Plan

### MVP

The MVP focuses on the core payment flow: users, wallets, deposits, transfers, and transaction history.

#### 1. Project Setup

- Create a Spring Boot project
- Configure Java 21
- Configure Gradle
- Connect PostgreSQL
- Add basic application configuration

#### 2. Core Domain

Create the main domain entities:

- User
- Wallet
- Transaction

Add basic enums:

- TransactionType
- TransactionStatus

Initial transaction types:

- DEPOSIT
- TRANSFER
- WITHDRAWAL

Initial transaction statuses:

- CREATED
- SUCCESS
- FAILED

#### 3. Wallet Logic

Implement basic wallet operations:

- create a wallet for a user
- get wallet balance
- deposit money into a wallet
- transfer money between wallets
- reject transfers when the sender has insufficient balance

#### 4. REST API

Create the first REST endpoints:

- create user
- get user by id
- create wallet
- get wallet by id
- deposit money
- transfer money
- get transaction history for a wallet

#### 5. Database

Store all core data in PostgreSQL:

- users
- wallets
- transactions

Use JPA relationships:

- User to Wallet
- Wallet to Transaction

#### 6. Basic Validation

Add validation for important rules:

- amount must be positive
- wallet must exist
- sender and receiver must be different
- balance cannot become negative

#### 7. Basic UI

Create a simple UI for the main flow:

- user list
- wallet balance
- deposit form
- transfer form
- transaction history

### After MVP

After the MVP is complete, the project can be expanded with authentication, roles, merchant flows, admin tools, and more realistic payment behavior.

#### 1. Authentication and Roles

Add authentication and role-based access.

Planned roles:

- USER
- ADMIN
- MERCHANT
- SUPPORT

Features:

- registration
- login
- password hashing
- JWT authentication
- role-based API access

#### 2. Admin Panel

Add admin features:

- view all users
- block and unblock users
- view all wallets
- view all transactions
- filter transactions by status, type, and date

#### 3. Merchant Flow

Add merchant-related features:

- merchant accounts
- invoice creation
- invoice payment
- merchant transaction history
- payment status tracking

#### 4. Refunds

Add refund logic:

- create refund request
- approve or reject refund
- update original transaction status
- store refund transaction

#### 5. Audit Logs

Store important system events:

- user registration
- wallet creation
- deposits
- transfers
- failed payments
- admin actions

#### 6. Better UI

Improve the frontend:

- authentication screens
- dashboard
- wallet page
- transaction filters
- admin panel
- merchant panel

#### 7. API Documentation

Add API documentation:

- Swagger / OpenAPI
- endpoint descriptions
- request and response examples

#### 8. Docker

Add Docker support:

- Dockerfile for backend
- Docker Compose for backend and PostgreSQL
- environment variables for configuration

#### 9. Testing

Add tests for important logic:

- wallet creation
- deposit
- transfer
- insufficient balance
- transaction history
- role-based access later
