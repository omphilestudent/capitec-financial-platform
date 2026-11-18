# Capitec Financial Intelligence Platform

A full-stack financial services platform built to demonstrate modern banking technology, microservices architecture, secure transaction processing, event-driven communication, and fraud detection.

The platform aims to simulate core banking capabilities through independently developed services that communicate using REST APIs and Apache Kafka.

## Project Overview

The Capitec Financial Intelligence Platform focuses on three key capabilities:

1. **Transaction Aggregation API**  
   Provides a centralised interface for retrieving, aggregating, and managing financial transaction data.

2. **Fraud Detection Engine**  
   Evaluates financial transactions against configurable fraud detection rules, identifies suspicious activity, and generates fraud alerts.

3. **Secure Statement Delivery**  
   Supports the generation and secure delivery of customer financial statements.

The project is being developed incrementally, starting with the core service architecture and expanding into transaction processing, event streaming, fraud detection, and frontend integration.

## Technology Stack

### Backend
- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Apache Kafka
- Maven
- REST APIs

### Frontend
- React
- JavaScript
- Vite
- CSS

### DevOps and Infrastructure
- Docker
- Docker Compose
- Git
- GitHub
- Maven Wrapper

## Architecture

The platform follows a **microservices architecture** with event-driven communication.

Each microservice is responsible for a specific business capability and is designed to operate independently. Services use REST APIs for synchronous communication and Apache Kafka for asynchronous event processing.

### Planned Microservices

| Service | Responsibility |
|---|---|
| API Gateway | Central entry point for client requests and service routing |
| Customer Service | Customer registration and customer information management |
| Account Service | Bank account management and account information |
| Transaction Service | Transaction creation, retrieval, and aggregation |
| Payment Service | Payment initiation and processing |
| Fraud Detection Service | Transaction monitoring and fraud rule evaluation |
| Notification Service | Customer notifications and event-driven alerts |

### Communication

- **Synchronous:** REST APIs for direct service-to-service communication.
- **Asynchronous:** Apache Kafka for publishing and consuming business events.
- **Persistence:** PostgreSQL databases, with service-level data ownership.

### Architecture Overview

```text
                    React Frontend
                          |
                          v
                     API Gateway
                          |
        +-----------------+-----------------+
        |                 |                 |
        v                 v                 v
 Customer Service   Account Service   Transaction Service
        |                 |                 |
        v                 v                 v
  Customer DB         Account DB       Transaction DB
                          |
                          v
                    Apache Kafka
                          |
          +---------------+---------------+
          |               |               |
          v               v               v
    Payment Service  Fraud Service  Notification Service
          |               |               |
          v               v               v
      Payment DB       Fraud DB      Notification DB
```

*Architecture is a target design and will evolve as services are implemented.*

## Event-Driven Processing

Apache Kafka will provide asynchronous communication between services.

Example transaction processing flow:

1. A transaction is submitted through the API.
2. The Transaction Service validates and persists the transaction.
3. A transaction event is published to Kafka.
4. The Fraud Detection Service consumes the event.
5. Fraud rules are evaluated against the transaction.
6. A fraud alert event is published when suspicious activity is detected.
7. The Notification Service consumes the alert and initiates customer notification.

This approach supports loose coupling between services and provides a foundation for scalable event processing.

## Security

Security is a core consideration throughout the development of the platform.

Planned security capabilities include:

- Spring Security integration.
- Authentication and authorisation.
- Role-based access control.
- Input validation.
- Secure REST API endpoints.
- Secure handling of customer and financial information.
- Protected statement delivery.

## Database Strategy

PostgreSQL is used for relational data persistence.

The microservices architecture follows database-per-service ownership, meaning each service manages its own data and does not directly access another service's database.

Cross-service data requirements will be handled through APIs and asynchronous events.

## Project Structure

The repository is organised around independently developed services and supporting infrastructure.

```text
capitec-fin-platform/
│
├── backend/
│   └── Existing backend application
│
├── customer-service/
│   └── Customer microservice
│
├── frontend/
│   └── React application
│
├── infrastructure/
│   └── Infrastructure configuration
│
├── docs/
│   └── Architecture and technical documentation
│
├── docker-compose.yml
├── .github/
└── README.md
```

Additional microservices will be introduced as development progresses.

## Getting Started

### Prerequisites

Ensure the following tools are installed:

- Java 21
- Maven
- Node.js and npm
- Docker Desktop
- Git

### Clone the Repository

```bash
git clone <repository-url>
cd capitec-fin-platform
```

### Start PostgreSQL

```bash
docker compose up -d postgres
```

### Run the Customer Service

```bash
cd customer-service
./mvnw spring-boot:run
```

The Customer Service is configured to run on:

```text
http://localhost:8081
```

Health endpoint:

```text
http://localhost:8081/actuator/health
```

*The database and service configuration must be in place before starting the application.*

## Development Roadmap

- [x] Establish initial Spring Boot backend.
- [x] Configure PostgreSQL connectivity.
- [x] Create the Customer Service project structure.
- [x] Configure independent Customer Service database.
- [ ] Complete Customer Service REST APIs.
- [ ] Implement Account Service.
- [ ] Implement Transaction Service.
- [ ] Introduce Apache Kafka event streaming.
- [ ] Implement Payment Service.
- [ ] Develop Fraud Detection Engine.
- [ ] Implement Notification Service.
- [ ] Introduce API Gateway.
- [ ] Integrate React frontend.
- [ ] Implement authentication and authorisation.
- [ ] Containerise all services using Docker Compose.
- [ ] Add automated testing and CI/CD pipelines.

## Engineering Objectives

This project is intended to demonstrate practical engineering skills in:

- Distributed systems and microservices.
- Java and Spring Boot application development.
- REST API design and integration.
- Event-driven architecture with Apache Kafka.
- Relational database design and data ownership.
- Authentication and application security.
- Docker-based development environments.
- Frontend and backend integration.
- Automated testing and continuous integration.

## Project Status

**Currently under active development.**

The platform is being built incrementally, with the initial focus on establishing independent microservices, database separation, and the foundations for Kafka-based event-driven communication.

## Author

**Omphile Mohlala**

Full Stack Software Engineering Project

Technologies: Java | Spring Boot | React | PostgreSQL | Apache Kafka | Docker
