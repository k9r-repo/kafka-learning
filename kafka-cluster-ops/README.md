# Kafka Cluster Operations & Monitoring

> **Project 3:** Production-ready Kafka cluster with comprehensive monitoring, chaos engineering, and operational best practices.

## 🎯 Project Overview

This project demonstrates enterprise-grade Kafka cluster operations including:
- Multi-broker Kafka cluster (3 brokers)  
- Zookeeper ensemble (3 nodes) for fault tolerance
- Real-time metrics collection with Prometheus
- Custom Grafana dashboards for visualization
- Automated data generation for testing
- Chaos engineering scripts for resilience testing
- Performance benchmarking tools
- Operational runbooks for common scenarios

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kafka Cluster (3 Brokers)                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐             │
│  │  Kafka-1    │  │  Kafka-2    │  │  Kafka-3    │             │
│  │  :9092      │  │  :9093      │  │  :9094      │             │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘             │
│         │                 │                 │                     │
│         └─────────────────┴─────────────────┘                    │
│                           │                                       │
│         ┌─────────────────┴─────────────────┐                    │
│         │                                   │                     │
│  ┌──────▼──────┐  ┌──────────────┐  ┌──────▼──────┐           │
│  │ Zookeeper-1 │  │ Zookeeper-2  │  │ Zookeeper-3 │           │
│  │    :2181    │  │    :2182     │  │    :2183    │           │
│  └─────────────┘  └──────────────┘  └─────────────┘           │
└─────────────────────────────────────────────────────────────────┘
                           │
                ┌──────────┴──────────┐
                │                      │
        ┌───────▼────────┐    ┌───────▼────────┐
        │ Kafka Exporter  │    │ Data Generator │
        │    :9308       │    │  (Python)      │
        └───────┬────────┘    └────────────────┘
                │
        ┌───────▼────────┐
        │  Prometheus    │
        │    :9090       │
        └───────┬────────┘
                │
        ┌───────▼────────┐
        │   Grafana      │
        │    :3000       │
        └────────────────┘
```

## 📊 Monitoring Stack

### Components
- **Kafka Exporter**: Scrapes metrics from all Kafka brokers
- **Prometheus**: Time-series database storing metrics
- **Grafana**: Visualization dashboards (login: admin/admin)
- **Kafka UI**: Web-based cluster management (:8080)

### Key Metrics Monitored
- Active broker count
- Messages per second (by topic)
- Consumer group lag
- Partition distribution
- Replica synchronization status
- Topic counts and health

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- PowerShell (Windows) or Bash (Linux/Mac)
- 8GB RAM minimum
- Python 3.9+ (optional, for local testing)

### 1. Start the Cluster

```powershell
cd kafka-cluster-ops
docker-compose up -d
```

Wait ~30 seconds for all services to start.

### 2. Verify Cluster Health

```powershell
# Check all containers are running
docker-compose ps

# Verify broker connectivity
docker exec kafka-1 kafka-broker-api-versions --bootstrap-server kafka-1:29092
```

### 3. Create Test Topics

```powershell
cd scripts
.\create-topics.ps1
```

### 4. Access Monitoring

- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8080

## 🧪 Testing & Operations

### Run Chaos Engineering Test

```powershell
cd scripts
.\chaos-broker-failure.ps1
```

**What it does:**
- Stops one Kafka broker
- Verifies leader election works
- Tests that producers/consumers continue working
- Restarts broker and verifies recovery

### Run Performance Benchmark

```powershell
cd scripts
.\performance-benchmark.ps1
```

**Metrics:**
- Producer throughput (records/sec, MB/sec)
- Consumer throughput  
- End-to-end latency

## 📖 Key Concepts Explained

### Replication Factor
```
Replication Factor = 3
```
- Each partition is copied to 3 different brokers
- If 1 broker fails, data is still available on 2 others
- **Min ISR = 2** ensures at least 2 replicas are in-sync

### ISR (In-Sync Replicas)
- List of replicas that are fully caught up with the leader
- If ISR < Replication Factor → **Problem!** (lagging replicas)
- Check with: `kafka-topics --describe --topic <topic>`

### Leader Election
- Each partition has 1 leader broker (handles reads/writes)
- If leader fails, Kafka automatically promotes a follower from ISR
- Zero downtime for clients!

## 🔧 Operational Runbook

### Check Cluster Status
```bash
# List all topics
docker exec kafka-1 kafka-topics --list --bootstrap-server kafka-1:29092

# Describe a topic (shows leaders, ISR)
docker exec kafka-1 kafka-topics --describe --topic user-events --bootstrap-server kafka-1:29092

# Check consumer group lag
docker exec kafka-1 kafka-consumer-groups --bootstrap-server kafka-1:29092 --group <group-name> --describe
```

### Handle Broker Failure
```bash
# Check which broker is down
docker-compose ps

# Verify cluster is still functioning
docker exec kafka-1 kafka-topics --describe --bootstrap-server kafka-1:29092,kafka-2:29092

# Restart failed broker
docker-compose up -d <broker-name>

# Verify broker rejoined and ISRs are healthy
docker exec kafka-1 kafka-topics --describe --bootstrap-server kafka-1:29092
```

### Scale Topics (Add Partitions)
```bash
docker exec kafka-1 kafka-topics --alter \
  --topic user-events \
  --partitions 12 \
  --bootstrap-server kafka-1:29092
```

⚠️ **Note:** You cannot decrease partitions!

### Troubleshooting

#### Problem: Consumer Lag Increasing
**Check:**
1. Is consumer healthy? (`docker logs <consumer-container>`)
2. Is partition count sufficient? (add partitions)
3. Is consumer processing slowly? (scale consumer instances)

#### Problem: Under-replicated Partitions
**Check:**
1. Are all brokers running? (`docker-compose ps`)
2. Check disk space on brokers
3. Check network connectivity between brokers

## 📁 Project Structure

```
kafka-cluster-ops/
├── docker-compose.yml           # Infrastructure definition
├── monitoring/
│   ├── prometheus.yml           # Prometheus scrape config
│   └── jmx-exporter-config.yml  # JMX metrics config
├── dashboards/
│   ├── kafka-cluster-monitoring.json  # Grafana dashboard
│   └── provisioning/            # Auto-provisioning configs
├── scripts/
│   ├── create-topics.ps1        # Topic creation
│   ├── chaos-broker-failure.ps1 # Chaos engineering
│   ├── performance-benchmark.ps1 # Performance tests
│   ├── data_generator.py        # Test data generation
│   ├── Dockerfile               # Data generator container
│   └── requirements.txt         # Python dependencies
└── README.md                    # This file
```

## 🎓 Learning Outcomes

After completing this project, you'll understand:

✅ **Cluster Architecture**
- Why 3 brokers and 3 Zookeepers (quorum, fault tolerance)
- How replication provides durability
- Leader election and failover mechanisms

✅ **Operations**
- Setting up production monitoring
- Running chaos engineering tests
- Performance benchmarking
- Troubleshooting common issues

✅ **Monitoring**
- Key Kafka metrics to track
- Setting up Prometheus + Grafana
- Building custom dashboards
- Alerting strategies

✅ **Best Practices**
- Replication factor = 3 (industry standard)
- Min ISR = 2 (balance durability and availability)
- Disable auto-create topics in production
- Monitor consumer lag religiously

## 🔗 Resources

- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Prometheus Docs](https://prometheus.io/docs/)
- [Grafana Tutorials](https://grafana.com/tutorials/)
- [Kafka Exporter](https://github.com/danielqsj/kafka_exporter)

## 📝 Notes

- Data generator produces ~1000 messages/sec across 4 topics
- Grafana dashboards auto-refresh every 5 seconds
- All data is persistent (Docker volumes)
- To reset: `docker-compose down -v` (deletes volumes!)

## 🏁 Next Steps

1. Add alerting rules in Prometheus
2. Integrate with ELK stack for log aggregation
3. Implement Kafka Streams for real-time processing
4. Set up Kafka Connect for data integration
5. Configure SSL/TLS for secure communication

---

**Project Author**: Your Name  
**Date**: March 2026  
**Purpose**: Kafka Learning Series - Project 3
