# Quick Start Guide - KSQLDB Fraud Detection

## 🚀 Get Started in 15 Minutes!

This guide will get you from zero to running fraud detection system.

---

## ✅ Prerequisites Check

Run these commands to verify:
```powershell
docker --version        # Should show 24.x or higher
docker-compose --version   # Should show v2.x
java -version          # Should show Java 11 or 17
python --version       # Should show Python 3.9+
```

---

## 📦 Step 1: Build the Custom UDF (5 minutes)

### Option A: Using IntelliJ IDEA (Recommended)

1. Open IntelliJ IDEA
2. **File → Open** → Navigate to:
   ```
   C:\Users\ADMIN\Documents\projects\kafka-learning\ksqldb-fraud-detection\custom-udf
   ```
3. IntelliJ will detect the Maven project
4. Open **Maven tool window** (right sidebar)
5. **Expand** `ksql-fraud-udf → Lifecycle`
6. **Double-click** `clean`
7. **Double-click** `package`
8. ✅ Wait for "BUILD SUCCESS" message

**Expected Output:**
```
[INFO] BUILD SUCCESS
[INFO] Total time: 15.432 s
```

**JAR Location:**
```
custom-udf\target\ksql-fraud-udf-1.0.0-jar-with-dependencies.jar
```

---

## 🐳 Step 2: Start Docker Services (3 minutes)

```powershell
cd C:\Users\ADMIN\Documents\projects\kafka-learning\ksqldb-fraud-detection

docker-compose up -d
```

**Wait 2-3 minutes for services to start.**

### Verify Services

```powershell
docker-compose ps
```

**Expected: All services should show "Up" or "healthy"**

```
NAME            STATUS
zookeeper       Up (healthy)
kafka           Up (healthy)
schema-registry Up (healthy)
ksqldb-server   Up (healthy)
ksqldb-cli      Up
kafka-ui        Up
```

### Check Logs (if issues)

```powershell
docker-compose logs ksqldb-server
```

---

## 🌐 Step 3: Verify Kafka UI (1 minute)

Open browser: **http://localhost:8080**

You should see:
- ✅ Kafka cluster: "fraud-detection-cluster"
- ✅ Broker: 1 broker online
- ✅ Topics: System topics listed
- ✅ KSQLDB: Connected

---

## 🔧 Step 4: Create KSQLDB Streams & Tables (3 minutes)

### Open KSQLDB CLI

```powershell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

You should see:
```
                  ===========================================
                  =       _              _ ____  ____       =
                  =      | | _____  __ _| |  _ \| __ )      =
                  =      | |/ / __|/ _` | | | | |  _ \      =
                  =      |   <\__ \ (_| | | |_| | |_) |     =
                  =      |_|\_\___/\__, |_|____/|____/      =
                  =                   |_|                   =
                  =        The Database purpose-built       =
                  =        for stream processing apps       =
                  ===========================================

ksql>
```

### Copy & Paste Queries

**A. Create Streams (from 01-create-streams.sql)**

Copy the entire contents of:
```
ksqldb-queries\01-create-streams.sql
```

Paste into KSQLDB CLI and press Enter.

**Expected Output:**
```
Stream TRANSACTIONS_STREAM created
Table USER_TRANSACTION_STATS created
Stream ENRICHED_TRANSACTIONS created
```

**B. Create Fraud Detection Logic (from 02-fraud-detection.sql)**

Copy the entire contents of:
```
ksqldb-queries\02-fraud-detection.sql
```

Paste into KSQLDB CLI.

**C. Create Windowed Aggregations (from 03-aggregations.sql)**

Copy the entire contents of:
```
ksqldb-queries\03-aggregations.sql
```

Paste into KSQLDB CLI.

### Verify Creation

```sql
SHOW STREAMS;
SHOW TABLES;
```

**You should see:**
- **Streams:** transactions_stream, enriched_transactions, fraud_alerts, etc.
- **Tables:** user_transaction_stats, velocity_fraud_5min, geo_fraud_hopping, etc.

**Leave this terminal open.**

---

## 🎲 Step 5: Start Transaction Generator (1 minute)

### Open NEW PowerShell Terminal

```powershell
cd C:\Users\ADMIN\Documents\projects\kafka-learning\ksqldb-fraud-detection\data-generator

# Install dependencies (first time only)
pip install -r requirements.txt

# Start generator
python transaction_generator.py
```

**Expected Output:**
```
================================================================================
   Real-Time Transaction Generator for Fraud Detection
   Kafka + KSQLDB Project
================================================================================

✓ Transaction Generator Initialized
  → Kafka Broker: localhost:9092
  → Topic: transactions
  → Card Pool Size: 50
  → Fraud Injection Rate: 15.0%

================================================================================
Starting Transaction Generation...
================================================================================

✓ Transaction: Card=****-3333, Amount=$125.50, Merchant=Amazon, Country=USA
⚠ FRAUD GENERATED: [VELOCITY] Card=****-1234, Amount=$450.00, Merchant=BestBuy, Country=USA
```

**Leave this running.**

---

## 📊 Step 6: View Real-Time Fraud Alerts (2 minutes)

### Go back to KSQLDB CLI terminal

```sql
-- View fraud alerts in real-time
SELECT * FROM fraud_alerts EMIT CHANGES;
```

**You should see fraud alerts appearing!**

```
+---------------+---------------+--------+----------------+------------+-------+
|transaction_id |card_number    |amount  |fraud_type      |fraud_score |country|
+---------------+---------------+--------+----------------+------------+-------+
|txn-a1b2c3d4   |4532-1111-2222 |5000.00 |AMOUNT_ANOMALY  |100         |USA    |
|txn-e5f6g7h8   |5123-4567-8901 |2500.00 |HIGH_RISK_...   |90          |Canada |
```

### Open Another Terminal for Velocity Fraud

```powershell
docker exec -it ksqldb-cli ksql http://ksqldb-server:8088
```

```sql
SELECT * FROM velocity_fraud_5min EMIT CHANGES;
```

---

## 🎯 Step 7: Explore Kafka UI

Open: **http://localhost:8080**

### View Topics
1. Click **Topics** in left sidebar
2. Find **transactions** topic
3. Click **Messages** tab
4. You should see transaction data streaming!

### View KSQLDB
1. Click **KSQLDB** in left sidebar
2. You'll see all your streams and tables
3. Can run queries directly from UI!

---

## 🛑 Stop Everything

When you're done:

### Stop Transaction Generator
- Press `Ctrl+C` in generator terminal

### Exit KSQLDB CLI
```sql
exit;
```

### Stop Docker Services
```powershell
cd C:\Users\ADMIN\Documents\projects\kafka-learning\ksqldb-fraud-detection
docker-compose down
```

---

## 🐛 Troubleshooting

### Issue: "UDF not found"

**Solution:** Rebuild UDF and restart KSQLDB:
```powershell
# In IntelliJ: Maven → clean → package
docker-compose restart ksqldb-server
```

### Issue: "No data in streams"

**Solution:** Check if generator is running:
```powershell
# Verify topic has messages
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic transactions \
  --from-beginning \
  --max-messages 5
```

### Issue: Services not starting

**Solution:** Check logs:
```powershell
docker-compose logs ksqldb-server
docker-compose logs kafka
```

Wait 3-5 minutes for services to fully start.

---

## ✅ Success Criteria

You've successfully completed setup if you see:

- ✅ Docker services all healthy
- ✅ Kafka UI accessible at http://localhost:8080
- ✅ Transaction generator printing transactions
- ✅ KSQLDB fraud_alerts showing real-time alerts
- ✅ Kafka UI showing messages in transactions topic

---

## 📸 Next Steps

1. **Take Screenshots** of:
   - Kafka UI dashboard
   - Fraud alerts in KSQLDB CLI
   - Transaction generator output
   - Kafka UI messages view

2. **Run Unit Tests**:
   ```powershell
   cd custom-udf
   # In IntelliJ: Maven → test
   ```

3. **Experiment**:
   - Try different fraud queries
   - Modify fraud thresholds
   - Add new fraud patterns

---

**Need Help?**
- Check `README.md` for detailed explanations
- Review `ARCHITECTURE.md` for system design
- See `ksqldb-queries/README.md` for query examples

**Time to Complete:** ~15 minutes  
**Difficulty:** Intermediate  
**Prerequisites:** Docker, Java, Python installed
