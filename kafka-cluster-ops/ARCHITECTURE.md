# Kafka Cluster Architecture

## System Overview

This document explains the architecture of our production-ready Kafka cluster setup.

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT APPLICATIONS                           │
│  (Producers, Consumers, Admin Tools, Data Generator)                 │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               │ Bootstrap: localhost:9092,9093,9094
                               │
┌──────────────────────────────▼───────────────────────────────────────┐
│                          KAFKA CLUSTER                                │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │   Kafka Broker 1 │  │   Kafka Broker 2 │  │   Kafka Broker 3 │  │
│  │                  │  │                  │  │                  │  │
│  │  External: 9092  │  │  External: 9093  │  │  External: 9094  │  │
│  │  Internal: 29092 │  │  Internal: 29092 │  │  Internal: 29092 │  │
│  │  JMX: 19092      │  │  JMX: 19093      │  │  JMX: 19094      │  │
│  │                  │  │                  │  │                  │  │
│  │  Broker ID: 1    │  │  Broker ID: 2    │  │  Broker ID: 3    │  │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘  │
└───────────┼────────────────────┼──────────────────────┼──────────────┘
            │                    │                      │
            └────────────────────┴──────────────────────┘
                                 │
                                 │ Coordination & Metadata
                                 │
┌────────────────────────────────▼─────────────────────────────────────┐
│                      ZOOKEEPER ENSEMBLE                               │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐        │
│  │  Zookeeper 1   │  │  Zookeeper 2   │  │  Zookeeper 3   │        │
│  │   Port: 2181   │  │   Port: 2182   │  │   Port: 2183   │        │
│  │  Server ID: 1  │  │  Server ID: 2  │  │  Server ID: 3  │        │
│  └────────────────┘  └────────────────┘  └────────────────┘        │
│         Quorum-based Consensus (needs 2/3 for decisions)             │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                       MONITORING STACK                                │
│                                                                       │
│  ┌────────────────┐           ┌────────────────┐                    │
│  │ Kafka Exporter │──scrapes──│  Kafka Cluster │                    │
│  │   Port: 9308   │           └────────────────┘                    │
│  └────────┬───────┘                                                  │
│           │ exposes metrics                                          │
│           ▼                                                          │
│  ┌────────────────┐           ┌────────────────┐                    │
│  │  Prometheus    │──────────>│   Grafana      │                    │
│  │  Port: 9090    │  queries  │   Port: 3000   │                    │
│  │ (Time-series)  │           │ (Dashboards)   │                    │
│  └────────────────┘           └────────────────┘                    │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                      MANAGEMENT & UTILITIES                           │
│                                                                       │
│  ┌────────────────┐           ┌────────────────┐                    │
│  │   Kafka UI     │           │ Data Generator │                    │
│  │   Port: 8080   │           │   (Python)     │                    │
│  │ (Web Console)  │           │ Produces test  │                    │
│  └────────────────┘           │  data 24/7     │                    │
│                               └────────────────┘                    │
└──────────────────────────────────────────────────────────────────────┘
```

## Data Flow

### 1. Message Production Flow
```
Producer → [Load Balancer] → Broker (Leader) → Replicas → Acknowledgment
```

1. Producer sends message to any broker (bootstrap servers)
2. Broker redirects to partition leader
3. Leader writes to log and replicates to followers
4. Once Min ISR replicas acknowledge, producer gets ack

### 2. Message Consumption Flow
```
Consumer → Broker (Leader) → Fetch Messages → Update Offsets → Commit
```

1. Consumer requests messages from partition leaders
2. Leader serves data from its log
3. Consumer processes messages
4. Consumer commits offset (can be auto or manual)

### 3. Monitoring Flow
```
Kafka Brokers → Kafka Exporter → Prometheus → Grafana Dashboard
      (JMX)      (HTTP /metrics)   (Scrape)    (Visualization)
```

## Network Architecture

### External Access (from host machine)
- **Kafka Brokers**: localhost:9092, localhost:9093, localhost:9094
- **Grafana**: http://localhost:3000
- **Prometheus**: http://localhost:9090
- **Kafka UI**: http://localhost:8080

### Internal Docker Network (kafka-net)
- **Kafka inter-broker**: kafka-1:29092, kafka-2:29092, kafka-3:29092
- **Zookeeper ensemble**: zookeeper-1:2181, zookeeper-2:2181, zookeeper-3:2181
- **Monitoring**: kafka-exporter:9308 → prometheus:9090 → grafana:3000

## Topic Architecture

### Example: `user-events` Topic
```
Topic: user-events
├── Partition 0: Leader=kafka-1, Replicas=[kafka-1, kafka-2, kafka-3], ISR=[1,2,3]
├── Partition 1: Leader=kafka-2, Replicas=[kafka-2, kafka-3, kafka-1], ISR=[2,3,1]
├── Partition 2: Leader=kafka-3, Replicas=[kafka-3, kafka-1, kafka-2], ISR=[3,1,2]
├── Partition 3: Leader=kafka-1, Replicas=[kafka-1, kafka-3, kafka-2], ISR=[1,3,2]
├── Partition 4: Leader=kafka-2, Replicas=[kafka-2, kafka-1, kafka-3], ISR=[2,1,3]
└── Partition 5: Leader=kafka-3, Replicas=[kafka-3, kafka-2, kafka-1], ISR=[3,2,1]
```

**Key Points:**
- 6 partitions for parallelism
- Replication factor = 3 (each partition on 3 brokers)
- Leaders evenly distributed (load balancing)
- All replicas in-sync (ISR = Replicas)

## Fault Tolerance

### Broker Failure Scenario
```
Normal State:
[Broker-1: Leader P0, P3]  [Broker-2: Leader P1, P4]  [Broker-3: Leader P2, P5]
        ↓
Broker-3 FAILS
        ↓
Failover (automatic):
[Broker-1: Leader P0, P2*, P3]  [Broker-2: Leader P1, P4, P5*]  [Broker-3: OFFLINE]
* = Promoted from follower to leader
        ↓
Broker-3 RECOVERS
        ↓
Recovery:
- Joins cluster
- Syncs replicas from leaders
- Becomes follower (not leader yet, until rebalancing)
```

### Zookeeper Failure Scenario
```
Normal State:
[ZK-1: Follower]  [ZK-2: Leader]  [ZK-3: Follower]
        ↓
ZK-2 (Leader) FAILS
        ↓
Election (quorum=2/3 still available):
[ZK-1: Leader*]  [ZK-2: OFFLINE]  [ZK-3: Follower]
* = Elected new leader
        ↓
Kafka cluster continues operating normally!
```

## Scaling Considerations

### Horizontal Scaling
- **Add brokers**: Increases cluster capacity
- **Rebalance partitions**: Distribute load to new brokers
- **Add partitions to topics**: Increases parallelism for consumers

### Vertical Scaling
- **Increase broker resources**: More CPU, RAM, disk
- **Tune JVM settings**: Heap size, GC tuning
- **Optimize Kafka configs**: Batch size, compression, flush intervals

## Performance Characteristics

### Throughput (typical)
- **Producer**: 100K-1M messages/sec per broker
- **Consumer**: 200K-2M messages/sec per broker
- **Replication**: ~80% of producer throughput

### Latency (typical)
- **End-to-end (acks=all)**: 5-10ms
- **Leader-only (acks=1)**: 1-5ms
- **Fire-and-forget (acks=0)**: <1ms

### Storage
- **Log retention**: Time-based (7 days default) or size-based
- **Log compaction**: Keep only latest value per key
- **Compression**: GZIP, Snappy, LZ4, Zstd

## Security (Future Enhancement)

### Planned Security Features
1. **SSL/TLS**: Encrypt data in transit
2. **SASL/SCRAM**: Username/password authentication
3. **ACLs**: Fine-grained authorization
4. **Encryption at rest**: Disk-level encryption

---

**Last Updated**: March 2026  
**Version**: 1.0
