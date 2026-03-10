FROM python:3.11-slim

WORKDIR /app

# Copy requirements and install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the generator script and config
COPY transaction_generator.py .
COPY config.json .

# Run the generator
CMD ["python", "transaction_generator.py"]
