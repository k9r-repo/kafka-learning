-- Healthcare Database Schema
-- Patient records for Kafka Connect source

CREATE TABLE IF NOT EXISTS patients (
    patient_id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    ssn VARCHAR(11),  -- Format: XXX-XX-XXXX (PII - will be masked by SMT)
    email VARCHAR(255),  -- PII - will be masked by SMT
    phone VARCHAR(20),  -- PII - will be masked by SMT
    address VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(2),
    zip_code VARCHAR(10),
    medical_record_number VARCHAR(50) UNIQUE NOT NULL,
    blood_type VARCHAR(5),
    allergies TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS appointments (
    appointment_id SERIAL PRIMARY KEY,
    patient_id INTEGER REFERENCES patients(patient_id),
    appointment_date TIMESTAMP NOT NULL,
    doctor_name VARCHAR(200),
    department VARCHAR(100),
    reason VARCHAR(500),
    status VARCHAR(50),  -- scheduled, completed, cancelled
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS medical_records (
    record_id SERIAL PRIMARY KEY,
    patient_id INTEGER REFERENCES patients(patient_id),
    visit_date DATE NOT NULL,
    diagnosis TEXT,
    treatment TEXT,
    medications TEXT,
    doctor_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample patient data (will be streamed to Kafka)
INSERT INTO patients (first_name, last_name, date_of_birth, ssn, email, phone, address, city, state, zip_code, medical_record_number, blood_type, allergies)
VALUES
    ('John', 'Doe', '1985-03-15', '123-45-6789', 'john.doe@email.com', '555-0101', '123 Main St', 'Boston', 'MA', '02101', 'MRN001', 'O+', 'Penicillin'),
    ('Jane', 'Smith', '1990-07-22', '987-65-4321', 'jane.smith@email.com', '555-0102', '456 Oak Ave', 'Cambridge', 'MA', '02139', 'MRN002', 'A+', 'None'),
    ('Robert', 'Johnson', '1978-11-30', '555-12-3456', 'robert.j@email.com', '555-0103', '789 Pine Rd', 'Somerville', 'MA', '02143', 'MRN003', 'B+', 'Aspirin, Latex'),
    ('Emily', 'Davis', '1995-05-18', '444-55-6677', 'emily.davis@email.com', '555-0104', '321 Elm St', 'Brookline', 'MA', '02445', 'MRN004', 'AB-', 'Sulfa drugs'),
    ('Michael', 'Wilson', '1982-09-08', '333-22-1111', 'michael.w@email.com', '555-0105', '654 Maple Dr', 'Newton', 'MA', '02458', 'MRN005', 'O-', 'None');

-- Insert sample appointments
INSERT INTO appointments (patient_id, appointment_date, doctor_name, department, reason, status)
VALUES
    (1, '2026-03-15 10:00:00', 'Dr. Sarah Williams', 'Cardiology', 'Annual checkup', 'scheduled'),
    (2, '2026-03-16 14:30:00', 'Dr. James Brown', 'Dermatology', 'Skin rash', 'scheduled'),
    (3, '2026-03-10 09:00:00', 'Dr. Lisa Garcia', 'Internal Medicine', 'Follow-up visit', 'completed'),
    (4, '2026-03-20 11:00:00', 'Dr. David Lee', 'Pediatrics', 'Vaccination', 'scheduled'),
    (5, '2026-03-08 15:00:00', 'Dr. Maria Rodriguez', 'Orthopedics', 'Knee pain', 'completed');

-- Insert sample medical records
INSERT INTO medical_records (patient_id, visit_date, diagnosis, treatment, medications, doctor_notes)
VALUES
    (1, '2026-02-01', 'Hypertension', 'Lifestyle changes, medication', 'Lisinopril 10mg daily', 'Patient advised to reduce sodium intake'),
    (2, '2026-01-15', 'Eczema', 'Topical treatment', 'Hydrocortisone cream', 'Apply twice daily, follow up in 2 weeks'),
    (3, '2026-03-10', 'Type 2 Diabetes', 'Diet control, medication', 'Metformin 500mg twice daily', 'HbA1c levels improved'),
    (5, '2026-03-08', 'Osteoarthritis', 'Physical therapy, pain management', 'Ibuprofen 400mg as needed', 'Refer to PT specialist');

-- Create indexes for performance
CREATE INDEX idx_patients_mrn ON patients(medical_record_number);
CREATE INDEX idx_appointments_patient ON appointments(patient_id);
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_medical_records_patient ON medical_records(patient_id);

-- Enable logical replication for CDC (Change Data Capture)
-- This is required for Debezium connector
ALTER TABLE patients REPLICA IDENTITY FULL;
ALTER TABLE appointments REPLICA IDENTITY FULL;
ALTER TABLE medical_records REPLICA IDENTITY FULL;

-- Grant permissions
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
