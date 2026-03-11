"""
Kafka Data Generator for Testing
Generates synthetic data to populate multiple topics for monitoring and testing
"""

import json
import random
import time
from datetime import datetime
from kafka import KafkaProducer
from kafka.errors import KafkaError

# Kafka configuration
BOOTSTRAP_SERVERS = ['kafka-1:29092', 'kafka-2:29092', 'kafka-3:29092']

# Topics to generate data for
TOPICS = {
    'user-events': {
        'schema': lambda: {
            'user_id': random.randint(1000, 9999),
            'event_type': random.choice(['login', 'logout', 'click', 'purchase', 'view']),
            'timestamp': datetime.now().isoformat(),
            'session_id': f"sess-{random.randint(10000, 99999)}"
        },
        'rate_ms': 100  # Produce every 100ms
    },
    'transactions': {
        'schema': lambda: {
            'transaction_id': f"tx-{random.randint(100000, 999999)}",
            'amount': round(random.uniform(10.0, 5000.0), 2),
            'currency': random.choice(['USD', 'EUR', 'GBP', 'JPY']),
            'merchant': random.choice(['Amazon', 'Walmart', 'Target', 'Best Buy', 'eBay']),
            'timestamp': datetime.now().isoformat()
        },
        'rate_ms': 200
    },
    'system-metrics': {
        'schema': lambda: {
            'server': f"server-{random.randint(1, 10)}",
            'cpu_usage': round(random.uniform(10.0, 95.0), 2),
            'memory_usage': round(random.uniform(30.0, 90.0), 2),
            'disk_usage': round(random.uniform(40.0, 85.0), 2),
            'timestamp': datetime.now().isoformat()
        },
        'rate_ms': 500
    },
    'iot-sensors': {
        'schema': lambda: {
            'sensor_id': f"sensor-{random.randint(1, 100)}",
            'temperature': round(random.uniform(15.0, 35.0), 2),
            'humidity': round(random.uniform(30.0, 80.0), 2),
            'pressure': round(random.uniform(980.0, 1020.0), 2),
            'timestamp': datetime.now().isoformat()
        },
        'rate_ms': 300
    }
}

def create_producer():
    """Create Kafka producer with retries"""
    return KafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_serializer=lambda v: json.dumps(v).encode('utf-8'),
        acks='all',  # Wait for all replicas
        retries=5,
        max_in_flight_requests_per_connection=1
    )

def send_message(producer, topic, message):
    """Send message to Kafka topic"""
    try:
        future = producer.send(topic, value=message)
        record_metadata = future.get(timeout=10)
        return True
    except KafkaError as e:
        print(f"Error sending to {topic}: {e}")
        return False

def main():
    print("🚀 Starting Kafka Data Generator...")
    print(f"📡 Connecting to: {BOOTSTRAP_SERVERS}")
    
    try:
        producer = create_producer()
        print("✅ Producer connected!")
        print(f"📊 Generating data for {len(TOPICS)} topics...")
        print("-" * 60)
        
        message_count = {topic: 0 for topic in TOPICS}
        last_send_time = {topic: 0 for topic in TOPICS}
        
        while True:
            current_time = time.time() * 1000  # Current time in milliseconds
            
            for topic, config in TOPICS.items():
                # Check if it's time to send message for this topic
                if current_time - last_send_time[topic] >= config['rate_ms']:
                    message = config['schema']()
                    
                    if send_message(producer, topic, message):
                        message_count[topic] += 1
                        last_send_time[topic] = current_time
                        
                        # Print progress every 10 messages per topic
                        if message_count[topic] % 10 == 0:
                            print(f"[{topic:20}] {message_count[topic]:5} messages sent")
            
            time.sleep(0.05)  # Sleep 50ms between checks
            
    except KeyboardInterrupt:
        print("\n\n⏹️  Stopping data generator...")
        print("\n📈 Final Statistics:")
        print("-" * 60)
        for topic, count in message_count.items():
            print(f"{topic:20}: {count:,} messages")
        print("-" * 60)
        
    except Exception as e:
        print(f"❌ Error: {e}")
        
    finally:
        if 'producer' in locals():
            producer.close()
            print("✅ Producer closed")

if __name__ == "__main__":
    main()
