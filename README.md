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