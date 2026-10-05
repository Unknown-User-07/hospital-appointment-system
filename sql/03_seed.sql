INSERT INTO specializations (name) VALUES ('Cardiology');
INSERT INTO specializations (name) VALUES ('Dermatology');
INSERT INTO specializations (name) VALUES ('Pediatrics');
INSERT INTO specializations (name) VALUES ('Orthopedics');
INSERT INTO specializations (name) VALUES ('General Practice');

INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'Alice', 'Morgan', 'alice.morgan@hospital.test', '+15550100001', specialization_id FROM specializations WHERE name = 'Cardiology';
INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'Ben', 'Okafor', 'ben.okafor@hospital.test', '+15550100002', specialization_id FROM specializations WHERE name = 'Dermatology';
INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'Chloe', 'Tanaka', 'chloe.tanaka@hospital.test', '+15550100003', specialization_id FROM specializations WHERE name = 'Pediatrics';
INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'David', 'Silva', 'david.silva@hospital.test', '+15550100004', specialization_id FROM specializations WHERE name = 'Orthopedics';
INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'Elena', 'Petrova', 'elena.petrova@hospital.test', '+15550100005', specialization_id FROM specializations WHERE name = 'General Practice';
INSERT INTO doctors (first_name, last_name, email, phone, specialization_id)
SELECT 'Farid', 'Haddad', 'farid.haddad@hospital.test', '+15550100006', specialization_id FROM specializations WHERE name = 'Cardiology';

-- Mon-Fri 09:00-13:00 for everyone (30-minute slots)
INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_minutes)
SELECT d.doctor_id, w.dow, '09:00', '13:00', 30
  FROM doctors d CROSS JOIN (SELECT LEVEL AS dow FROM dual CONNECT BY LEVEL <= 5) w;

-- Afternoon sessions Mon/Wed/Fri for some doctors (20-minute slots)
INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_minutes)
SELECT d.doctor_id, w.dow, '14:00', '17:00', 20
  FROM doctors d CROSS JOIN (SELECT 1 AS dow FROM dual UNION ALL SELECT 3 FROM dual UNION ALL SELECT 5 FROM dual) w
 WHERE d.last_name IN ('Morgan', 'Silva', 'Petrova');

INSERT INTO patients (first_name, last_name, date_of_birth, phone, email)
VALUES ('John', 'Doe', DATE '1990-05-17', '+15551230001', 'john.doe@example.com');

COMMIT;
