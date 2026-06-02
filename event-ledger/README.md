event-ledger/
├── event-gateway/
│   ├── src/main/java/com/eventledger/gateway/
│   │   ├── controller/
│   │   │   └── EventController.java
│   │   ├── service/
│   │   │   └── EventService.java
│   │   ├── client/
│   │   │   └── AccountServiceClient.java
│   │   ├── model/
│   │   │   ├── Event.java
│   │   │   └── EventStatus.java
│   │   ├── repository/
│   │   │   └── EventRepository.java
│   │   ├── resilience/
│   │   │   └── CircuitBreakerConfig.java
│   │   └── EventGatewayApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── account-service/
│   ├── src/main/java/com/eventledger/account/
│   │   ├── controller/
│   │   │   └── AccountController.java
│   │   ├── service/
│   │   │   └── AccountService.java
│   │   ├── model/
│   │   │   ├── Account.java
│   │   │   └── Transaction.java
│   │   ├── repository/
│   │   │   ├── AccountRepository.java
│   │   │   └── TransactionRepository.java
│   │   └── AccountServiceApplication.java
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
│
├── docker-compose.yml
└── README.md