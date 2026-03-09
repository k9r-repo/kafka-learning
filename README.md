# 🚀 Kafka Learning Portfolio

> A comprehensive collection of production-ready Apache Kafka projects demonstrating expertise in streaming data pipelines, real-time analytics, and distributed systems operations.

**Author:** Kalpesh  
**Tech Stack:** Apache Kafka, Kafka Connect, KSQLDB, Java, Spring Boot, Docker, PostgreSQL  
**Status:** Active Development

---

## 📋 Projects Overview

This repository contains **3 end-to-end Kafka projects** showcasing different aspects of the Kafka ecosystem:

| # | Project | Description | Key Technologies | Status |
|---|---------|-------------|------------------|--------|
| 1 | [Healthcare Data Pipeline](./kafka-connect-healthcare/) | CDC pipeline with custom PII masking SMT | Kafka Connect, Custom SMT (Java), JDBC Source | ✅ **Complete** |
| 2 | KSQLDB Fraud Detection | Real-time fraud detection with stream processing | KSQLDB, Stream Processing, Windowing | 🔄 **Coming Soon** |
| 3 | Kafka Cluster Operations | Secured multi-broker cluster with monitoring | ACLs, SSL/SASL, Prometheus, Grafana | 📋 **Planned** |

---

## 🎯 Project 1: Healthcare Data Pipeline with Custom SMT

**📁 Directory:** [`kafka-connect-healthcare/`](./kafka-connect-healthcare/)

### Overview
A production-ready data pipeline that captures patient records from PostgreSQL and streams them to Kafka with real-time PII (Personally Identifiable Information) masking using a custom Single Message Transform (SMT).

### Key Features
- ✅ **Custom Java SMT** for PII masking (SSN, email, phone)
- ✅ **JDBC Source Connector** with incremental+timestamp mode
- ✅ **Audit Trail** with transformation timestamps and versioning
- ✅ **Full Docker Stack** (Kafka, Zookeeper, Connect, PostgreSQL, Schema Registry, Kafka UI)
- ✅ **HIPAA-Compliant** data handling patterns

### Technologies
- **Kafka Connect:** Source connector configuration and deployment
- **Java 11:** Custom SMT development with Kafka Connect API
- **PostgreSQL:** Source database with CDC-ready configuration
- **Maven:** Build automation for custom plugins
- **Docker Compose:** Multi-container orchestration
- **Kafka UI:** Monitoring and verification

### Results
- 🎯 **100% PII Masking Success Rate**
- ⚡ **340ms Processing Time** for 6 messages (10 KB)
- 📊 **3 PII Fields Masked** per record (SSN, Email, Phone)

[📖 View Detailed Documentation →](./kafka-connect-healthcare/README.md)

---

## 🎯 Project 2: KSQLDB Fraud Detection System

**📁 Directory:** `ksqldb-fraud-detection/` *(Coming Soon)*

### Planned Features
- Real-time fraud detection using KSQLDB stream processing
- Custom User-Defined Functions (UDFs) for anomaly detection
- Windowed aggregations for transaction pattern analysis
- Real-time alerting system
- Interactive dashboards

---

## 🎯 Project 3: Secured Kafka Cluster Operations

**📁 Directory:** `kafka-cluster-operations/` *(Planned)*

### Planned Features
- Multi-broker Kafka cluster with replication
- SSL/SASL authentication and ACL authorization
- Topic management and partition rebalancing
- Monitoring with Prometheus and Grafana
- Zero-downtime upgrades and disaster recovery

---

## 🛠️ Skills Demonstrated

### Kafka Ecosystem
- ✅ Kafka Connect (Source/Sink Connectors)
- ✅ Custom Single Message Transforms (SMT) Development
- 🔄 KSQLDB Stream Processing *(Next)*
- 📋 Cluster Administration & Security *(Planned)*

### Programming & Development
- ✅ Java 11 (Kafka Connect API, Maven)
- 🔄 SQL & Stream Processing *(Next)*
- ✅ Docker & Docker Compose
- ✅ Git Version Control

### Data Engineering
- ✅ Change Data Capture (CDC)
- ✅ Real-time Data Transformation
- ✅ PII/PHI Data Security & Compliance
- 🔄 Stream Analytics *(Next)*

### DevOps & Operations
- ✅ Container Orchestration
- ✅ Service Health Monitoring
- ✅ Troubleshooting & Debugging
- 📋 Cluster Management *(Planned)*

---

## 🚀 Quick Start

### Prerequisites
- Docker Desktop (20.x or higher)
- Java 11 (for custom plugin development)
- Git

### Running a Project

Each project has its own self-contained environment. Navigate to the project folder and follow its README:

```bash
# Example: Running Project 1
cd kafka-connect-healthcare
docker-compose up -d
```

Detailed setup instructions are in each project's README file.

---

## 📚 Learning Path

This repository follows a structured learning approach:

1. **Foundation (Project 1):** Kafka Connect basics, custom transformations, data pipelines
2. **Intermediate (Project 2):** Stream processing, KSQLDB, real-time analytics
3. **Advanced (Project 3):** Cluster operations, security, production operations

Each project builds upon concepts from the previous one while introducing new challenges.

---

## 📖 Documentation

- **Architecture Diagrams:** Each project includes visual system architecture
- **Setup Guides:** Step-by-step instructions for local development
- **Screenshots:** Visual verification of working systems
- **Code Comments:** Detailed inline documentation
- **Project Summaries:** High-level overview of achievements and learnings

---

## 🎓 Interview Preparation

This repository serves as a portfolio demonstrating:

- **Hands-on Experience:** Real working code, not just tutorials
- **Problem-Solving:** Custom solutions for complex requirements (e.g., PII masking SMT)
- **Best Practices:** Production-ready patterns, error handling, documentation
- **Full-Stack Understanding:** From database to message broker to monitoring
- **DevOps Skills:** Containerization, orchestration, troubleshooting

---

## 🔗 Related Resources

- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Confluent Platform](https://docs.confluent.io/)
- [Kafka Connect API](https://kafka.apache.org/documentation/#connect)
- [KSQLDB Documentation](https://docs.ksqldb.io/)

---

## 📝 License

This repository is for educational and portfolio purposes.

---

## 📧 Contact

**Kalpesh**  
🔗 [LinkedIn](#) | 📧 [Email](#) | 💼 [Portfolio](#)

---

**⭐ If you find this repository helpful, please consider giving it a star!**

*Last Updated: March 9, 2026*
