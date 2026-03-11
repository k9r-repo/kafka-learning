# Chaos Engineering Script: Broker Failure Test
# Tests cluster resilience by stopping one broker at a time

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Kafka Chaos Engineering: Broker Failure" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Test 1: Stop Broker 3
Write-Host "[TEST 1] Stopping kafka-3..." -ForegroundColor Yellow
docker stop kafka-3

Write-Host "Waiting 10 seconds for cluster to rebalance..." -ForegroundColor Gray
Start-Sleep -Seconds 10

Write-Host "`nChecking topic leadership distribution..." -ForegroundColor Cyan
docker exec kafka-1 kafka-topics --describe --topic test-replicated --bootstrap-server kafka-1:29092

Write-Host "`n[TEST 1] kafka-3 is DOWN. Testing producer..." -ForegroundColor Yellow
docker exec kafka-1 kafka-console-producer --bootstrap-server kafka-1:29092,kafka-2:29092 --topic test-replicated <<< "Test message during failure"

Write-Host "`nRestarting kafka-3..." -ForegroundColor Green
docker start kafka-3

Write-Host "Waiting for kafka-3 to rejoin cluster..." -ForegroundColor Gray
Start-Sleep -Seconds 20

Write-Host "`n[RESULT] Cluster recovered! Checking ISR status..." -ForegroundColor Green
docker exec kafka-1 kafka-topics --describe --topic test-replicated --bootstrap-server kafka-1:29092

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  Chaos Test Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Key Observations:" -ForegroundColor Yellow
Write-Host "1. Did leadership shift when broker-3 stopped?" -ForegroundColor White
Write-Host "2. Were we able to produce messages with 2/3 brokers?" -ForegroundColor White
Write-Host "3. Did broker-3 rejoin and sync its replicas?" -ForegroundColor White
