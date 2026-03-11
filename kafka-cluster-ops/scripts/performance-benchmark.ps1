# Kafka Performance Benchmark Script
# Tests producer and consumer throughput

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Kafka Performance Benchmark" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Create benchmark topic
Write-Host "[SETUP] Creating benchmark topic..." -ForegroundColor Yellow
docker exec kafka-1 kafka-topics --create --if-not-exists `
    --topic benchmark-test `
    --bootstrap-server kafka-1:29092 `
    --replication-factor 3 `
    --partitions 12

Write-Host ""

# Producer Performance Test
Write-Host "[TEST 1] Producer Throughput Test" -ForegroundColor Cyan
Write-Host "Sending 1 million messages (1KB each)..." -ForegroundColor Gray
docker exec kafka-1 kafka-producer-perf-test `
    --topic benchmark-test `
    --num-records 1000000 `
    --record-size 1024 `
    --throughput -1 `
    --producer-props bootstrap.servers=kafka-1:29092,kafka-2:29092,kafka-3:29092 acks=all

Write-Host ""

# Consumer Performance Test
Write-Host "[TEST 2] Consumer Throughput Test" -ForegroundColor Cyan
Write-Host "Consuming all messages..." -ForegroundColor Gray
docker exec kafka-1 kafka-consumer-perf-test `
    --topic benchmark-test `
    --messages 1000000 `
    --bootstrap-server kafka-1:29092,kafka-2:29092,kafka-3:29092 `
    --group benchmark-group

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Benchmark Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Cleanup: Deleting benchmark topic..." -ForegroundColor Gray
docker exec kafka-1 kafka-topics --delete --topic benchmark-test --bootstrap-server kafka-1:29092
