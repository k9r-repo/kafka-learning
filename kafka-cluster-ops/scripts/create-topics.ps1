# Script to create test topics for monitoring
Write-Host "Creating test topics..." -ForegroundColor Cyan

$topics = @("user-events", "transactions", "system-metrics", "iot-sensors")

foreach ($topic in $topics) {
    Write-Host "Creating topic: $topic" -ForegroundColor Yellow
    docker exec kafka-1 kafka-topics --create --if-not-exists `
        --topic $topic `
        --bootstrap-server kafka-1:29092 `
        --replication-factor 3 `
        --partitions 6
}

Write-Host ""
Write-Host "All topics created successfully!" -ForegroundColor Green

Write-Host ""
Write-Host "Current topics:" -ForegroundColor Cyan
docker exec kafka-1 kafka-topics --list --bootstrap-server kafka-1:29092
