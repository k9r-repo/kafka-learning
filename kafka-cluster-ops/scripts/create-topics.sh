#!/bin/bash
# Script to create test topics for monitoring

echo "Creating test topics..."

# Create topics with replication factor 3
docker exec kafka-1 kafka-topics --create --if-not-exists \
  --topic user-events \
  --bootstrap-server kafka-1:29092 \
  --replication-factor 3 \
  --partitions 6

docker exec kafka-1 kafka-topics --create --if-not-exists \
  --topic transactions \
  --bootstrap-server kafka-1:29092 \
  --replication-factor 3 \
  --partitions 6

docker exec kafka-1 kafka-topics --create --if-not-exists \
  --topic system-metrics \
  --bootstrap-server kafka-1:29092 \
  --replication-factor 3 \
  --partitions 6

docker exec kafka-1 kafka-topics --create --if-not-exists \
  --topic iot-sensors \
  --bootstrap-server kafka-1:29092 \
  --replication-factor 3 \
  --partitions 6

echo "✅ All topics created successfully!"

# List topics
echo -e "\n📋 Current topics:"
docker exec kafka-1 kafka-topics --list --bootstrap-server kafka-1:29092
