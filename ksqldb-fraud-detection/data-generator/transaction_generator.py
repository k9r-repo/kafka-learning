#!/usr/bin/env python3
"""
Real-Time Transaction Data Generator for Fraud Detection System
Simulates credit card transactions with fraud patterns for KSQLDB processing
"""

import json
import random
import time
import uuid
import os
from datetime import datetime, timedelta
from kafka import KafkaProducer
from faker import Faker
from colorama import Fore, Style, init

# Initialize colorama for colored console output
init(autoreset=True)

class TransactionGenerator:
    """Generates realistic transaction data with fraud patterns"""
    
    def __init__(self, config_path='config.json'):
        """Initialize the generator with configuration"""
        with open(config_path, 'r') as f:
            self.config = json.load(f)
        
        # Allow overriding Kafka broker from environment variable
        kafka_broker = os.getenv('KAFKA_BOOTSTRAP_SERVERS', self.config['kafka_broker'])
        
        self.fake = Faker()
        self.producer = KafkaProducer(
            bootstrap_servers=kafka_broker,
            value_serializer=lambda v: json.dumps(v).encode('utf-8'),
            key_serializer=lambda k: k.encode('utf-8') if k else None
        )
        
        # Generate pool of card numbers for simulation
        self.card_pool = self._generate_card_pool(50)
        
        # Track user statistics for anomaly detection
        self.user_stats = {card: {'avg_amount': random.uniform(50, 300), 
                                   'std_dev': random.uniform(20, 80),
                                   'last_country': random.choice(self.config['countries']),
                                   'last_timestamp': None,
                                   'txn_count_5min': 0}
                          for card in self.card_pool}
        
        print(f"{Fore.GREEN}✓ Transaction Generator Initialized")
        print(f"{Fore.CYAN}  → Kafka Broker: {kafka_broker}")
        print(f"{Fore.CYAN}  → Topic: {self.config['topic_name']}")
        print(f"{Fore.CYAN}  → Card Pool Size: {len(self.card_pool)}")
        print(f"{Fore.CYAN}  → Fraud Injection Rate: {self.config['fraud_injection_rate'] * 100}%\n")
    
    def _generate_card_pool(self, count):
        """Generate a pool of card numbers"""
        cards = []
        for _ in range(count):
            # Generate realistic-looking card numbers (Luhn algorithm not implemented for simplicity)
            card = f"{random.choice(['4', '5'])}{random.randint(100, 999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"
            cards.append(card)
        return cards
    
    def _generate_normal_transaction(self):
        """Generate a normal (non-fraudulent) transaction"""
        card_number = random.choice(self.card_pool)
        stats = self.user_stats[card_number]
        
        # Normal amount based on user's average
        amount = max(5.0, random.gauss(stats['avg_amount'], stats['std_dev']))
        
        transaction = {
            'transaction_id': f"txn-{uuid.uuid4().hex[:8]}",
            'card_number': card_number,
            'cardholder_name': self.fake.name(),
            'amount': round(amount, 2),
            'merchant': self.fake.company(),
            'merchant_category': random.choice([cat for cat in self.config['merchant_categories'] 
                                               if cat not in self.config['high_risk_categories']]),
            'country': stats['last_country'],
            'city': self.fake.city(),
            'timestamp': int(datetime.now().timestamp() * 1000),
            'currency': 'USD',
            'fraud_label': False  # Ground truth for testing
        }
        
        return transaction
    
    def _generate_velocity_fraud(self):
        """Generate multiple rapid transactions (velocity fraud pattern)"""
        card_number = random.choice(self.card_pool)
        stats = self.user_stats[card_number]
        
        transactions = []
        base_time = datetime.now()
        
        # Generate 4-6 transactions within 2 minutes
        for i in range(random.randint(4, 6)):
            amount = round(random.uniform(50, 500), 2)
            timestamp = base_time + timedelta(seconds=i * random.randint(10, 30))
            
            transaction = {
                'transaction_id': f"txn-{uuid.uuid4().hex[:8]}",
                'card_number': card_number,
                'cardholder_name': self.fake.name(),
                'amount': amount,
                'merchant': self.fake.company(),
                'merchant_category': random.choice(self.config['merchant_categories']),
                'country': stats['last_country'],
                'city': self.fake.city(),
                'timestamp': int(timestamp.timestamp() * 1000),
                'currency': 'USD',
                'fraud_label': True,
                'fraud_type': 'VELOCITY'
            }
            transactions.append(transaction)
        
        return transactions
    
    def _generate_amount_anomaly_fraud(self):
        """Generate transaction with unusually high amount"""
        card_number = random.choice(self.card_pool)
        stats = self.user_stats[card_number]
        
        # Amount significantly higher than user's average (3-5x)
        anomaly_multiplier = random.uniform(3, 5)
        amount = round(stats['avg_amount'] * anomaly_multiplier, 2)
        
        transaction = {
            'transaction_id': f"txn-{uuid.uuid4().hex[:8]}",
            'card_number': card_number,
            'cardholder_name': self.fake.name(),
            'amount': amount,
            'merchant': self.fake.company(),
            'merchant_category': random.choice(self.config['merchant_categories']),
            'country': stats['last_country'],
            'city': self.fake.city(),
            'timestamp': int(datetime.now().timestamp() * 1000),
            'currency': 'USD',
            'fraud_label': True,
            'fraud_type': 'AMOUNT_ANOMALY'
        }
        
        return transaction
    
    def _generate_geo_anomaly_fraud(self):
        """Generate transactions from different countries within short time"""
        card_number = random.choice(self.card_pool)
        stats = self.user_stats[card_number]
        
        transactions = []
        base_time = datetime.now()
        
        # Generate 2-3 transactions from different countries within 30 minutes
        different_countries = random.sample(
            [c for c in self.config['countries'] if c != stats['last_country']], 
            k=min(2, len(self.config['countries']) - 1)
        )
        
        for i, country in enumerate(different_countries):
            timestamp = base_time + timedelta(minutes=i * random.randint(5, 15))
            
            transaction = {
                'transaction_id': f"txn-{uuid.uuid4().hex[:8]}",
                'card_number': card_number,
                'cardholder_name': self.fake.name(),
                'amount': round(random.uniform(100, 800), 2),
                'merchant': self.fake.company(),
                'merchant_category': random.choice(self.config['merchant_categories']),
                'country': country,
                'city': self.fake.city(),
                'timestamp': int(timestamp.timestamp() * 1000),
                'currency': 'USD',
                'fraud_label': True,
                'fraud_type': 'GEO_ANOMALY'
            }
            transactions.append(transaction)
        
        return transactions
    
    def _generate_high_risk_merchant_fraud(self):
        """Generate transaction with high-risk merchant category"""
        card_number = random.choice(self.card_pool)
        stats = self.user_stats[card_number]
        
        transaction = {
            'transaction_id': f"txn-{uuid.uuid4().hex[:8]}",
            'card_number': card_number,
            'cardholder_name': self.fake.name(),
            'amount': round(random.uniform(500, 3000), 2),
            'merchant': self.fake.company(),
            'merchant_category': random.choice(self.config['high_risk_categories']),
            'country': stats['last_country'],
            'city': self.fake.city(),
            'timestamp': int(datetime.now().timestamp() * 1000),
            'currency': 'USD',
            'fraud_label': True,
            'fraud_type': 'HIGH_RISK_MERCHANT'
        }
        
        return transaction
    
    def _send_transaction(self, transaction):
        """Send transaction to Kafka topic"""
        try:
            future = self.producer.send(
                self.config['topic_name'],
                key=transaction['card_number'],
                value=transaction
            )
            
            # Block until message is sent (synchronous for demo purposes)
            future.get(timeout=10)
            
            # Color code based on fraud status
            if transaction.get('fraud_label', False):
                color = Fore.RED
                fraud_type = transaction.get('fraud_type', 'UNKNOWN')
                print(f"{color}⚠ FRAUD GENERATED: [{fraud_type}] "
                      f"Card={transaction['card_number'][-4:]}, "
                      f"Amount=${transaction['amount']:.2f}, "
                      f"Merchant={transaction['merchant'][:20]}, "
                      f"Country={transaction['country']}")
            else:
                color = Fore.GREEN
                print(f"{color}✓ Transaction: "
                      f"Card=****-{transaction['card_number'][-4:]}, "
                      f"Amount=${transaction['amount']:.2f}, "
                      f"Merchant={transaction['merchant'][:20]}, "
                      f"Country={transaction['country']}")
            
            return True
        
        except Exception as e:
            print(f"{Fore.YELLOW}✗ Error sending transaction: {str(e)}")
            return False
    
    def generate_and_send(self):
        """Main loop to generate and send transactions"""
        print(f"\n{Fore.CYAN}{'='*80}")
        print(f"{Fore.CYAN}Starting Transaction Generation...")
        print(f"{Fore.CYAN}{'='*80}\n")
        
        transaction_count = 0
        fraud_count = 0
        
        try:
            while True:
                # Decide whether to generate fraud or normal transaction
                if random.random() < self.config['fraud_injection_rate']:
                    # Choose fraud type
                    fraud_type = random.choice([
                        'velocity', 
                        'amount_anomaly', 
                        'geo_anomaly', 
                        'high_risk_merchant'
                    ])
                    
                    if fraud_type == 'velocity':
                        transactions = self._generate_velocity_fraud()
                    elif fraud_type == 'amount_anomaly':
                        transactions = [self._generate_amount_anomaly_fraud()]
                    elif fraud_type == 'geo_anomaly':
                        transactions = self._generate_geo_anomaly_fraud()
                    else:  # high_risk_merchant
                        transactions = [self._generate_high_risk_merchant_fraud()]
                    
                    # Send all transactions in the fraud pattern
                    for txn in transactions:
                        if self._send_transaction(txn):
                            transaction_count += 1
                            fraud_count += 1
                        time.sleep(0.1)  # Small delay between rapid transactions
                
                else:
                    # Generate normal transaction
                    transaction = self._generate_normal_transaction()
                    if self._send_transaction(transaction):
                        transaction_count += 1
                
                # Print statistics every 20 transactions
                if transaction_count % 20 == 0:
                    fraud_rate = (fraud_count / transaction_count * 100) if transaction_count > 0 else 0
                    print(f"\n{Fore.YELLOW}📊 Stats: Total={transaction_count}, "
                          f"Fraud={fraud_count} ({fraud_rate:.1f}%), "
                          f"Normal={transaction_count - fraud_count}\n")
                
                # Wait before next transaction
                time.sleep(self.config['generation_rate_ms'] / 1000.0)
        
        except KeyboardInterrupt:
            print(f"\n\n{Fore.CYAN}{'='*80}")
            print(f"{Fore.CYAN}Generation Stopped by User")
            print(f"{Fore.CYAN}{'='*80}")
            print(f"{Fore.GREEN}Total Transactions: {transaction_count}")
            print(f"{Fore.RED}Fraudulent: {fraud_count}")
            print(f"{Fore.GREEN}Normal: {transaction_count - fraud_count}")
            print(f"{Fore.YELLOW}Fraud Rate: {(fraud_count / transaction_count * 100):.2f}%\n")
        
        finally:
            self.producer.close()
            print(f"{Fore.CYAN}Kafka producer closed.\n")


def main():
    """Entry point for the transaction generator"""
    print(f"\n{Fore.MAGENTA}{'='*80}")
    print(f"{Fore.MAGENTA}   Real-Time Transaction Generator for Fraud Detection")
    print(f"{Fore.MAGENTA}   Kafka + KSQLDB Project")
    print(f"{Fore.MAGENTA}{'='*80}\n")
    
    try:
        generator = TransactionGenerator()
        generator.generate_and_send()
    except FileNotFoundError:
        print(f"{Fore.RED}✗ Error: config.json not found!")
        print(f"{Fore.YELLOW}  Make sure config.json is in the same directory.")
    except Exception as e:
        print(f"{Fore.RED}✗ Fatal Error: {str(e)}")
        import traceback
        traceback.print_exc()


if __name__ == '__main__':
    main()
