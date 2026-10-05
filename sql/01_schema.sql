-- =====================================================================
-- Hospital Appointment Booking System - Oracle 12c+ schema (3NF)
-- Run as the application schema owner (SQL Developer: "Run Script" F5).
-- =====================================================================

-- Optional clean re-run
-- DROP TABLE appointments CASCADE CONSTRAINTS PURGE;
-- DROP TABLE doctor_schedules CASCADE CONSTRAINTS PURGE;
-- DROP TABLE doctors CASCADE CONSTRAINTS PURGE;
-- DROP TABLE patients CASCADE CONSTRAINTS PURGE;
-- DROP TABLE specializations CASCADE CONSTRAINTS PURGE;

CREATE TABLE specializations (
    specialization_id NUMBER GENERATED ALWAYS AS IDENTITY,
    name              VARCHAR2(100) NOT NULL,
    CONSTRAINT pk_specializations PRIMARY KEY (specialization_id),
    CONSTRAINT uq_specialization_name UNIQUE (name)
);

CREATE TABLE doctors (
    doctor_id         NUMBER GENERATED ALWAYS AS IDENTITY,
    first_name        VARCHAR2(50)  NOT NULL,
    last_name         VARCHAR2(50)  NOT NULL,
    email             VARCHAR2(120) NOT NULL,
    phone             VARCHAR2(20),
    specialization_id NUMBER        NOT NULL,
    is_active         CHAR(1)       DEFAULT 'Y' NOT NULL,
    CONSTRAINT pk_doctors PRIMARY KEY (doctor_id),
    CONSTRAINT uq_doctor_email UNIQUE (email),
    CONSTRAINT ck_doctor_active CHECK (is_active IN ('Y','N')),
    -- NO ACTION (Oracle default): a specialization in use cannot be deleted
    CONSTRAINT fk_doctor_spec FOREIGN KEY (specialization_id)
        REFERENCES specializations (specialization_id)
);

CREATE TABLE patients (
    patient_id    NUMBER GENERATED ALWAYS AS IDENTITY,
    first_name    VARCHAR2(50)  NOT NULL,
    last_name     VARCHAR2(50)  NOT NULL,
    date_of_birth DATE          NOT NULL,
    phone         VARCHAR2(20)  NOT NULL,
    email         VARCHAR2(120),
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_patients PRIMARY KEY (patient_id),
    CONSTRAINT uq_patient_phone UNIQUE (phone),
    CONSTRAINT ck_patient_dob CHECK (date_of_birth >= DATE '1900-01-01')
);

-- Weekly recurring availability template. day_of_week: ISO 1=Mon .. 7=Sun
CREATE TABLE doctor_schedules (
    schedule_id  NUMBER GENERATED ALWAYS AS IDENTITY,
    doctor_id    NUMBER       NOT NULL,
    day_of_week  NUMBER(1)    NOT NULL,
    start_time   VARCHAR2(5)  NOT NULL,   -- 'HH24:MI'
    end_time     VARCHAR2(5)  NOT NULL,
    slot_minutes NUMBER(3)    DEFAULT 30 NOT NULL,
    CONSTRAINT pk_doctor_schedules PRIMARY KEY (schedule_id),
    CONSTRAINT uq_schedule_window UNIQUE (doctor_id, day_of_week, start_time),
    CONSTRAINT ck_sched_dow   CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_sched_start CHECK (REGEXP_LIKE(start_time, '^([01][0-9]|2[0-3]):[0-5][0-9]$')),
    CONSTRAINT ck_sched_end   CHECK (REGEXP_LIKE(end_time,   '^([01][0-9]|2[0-3]):[0-5][0-9]$')),
    CONSTRAINT ck_sched_order CHECK (start_time < end_time),
    CONSTRAINT ck_sched_slot  CHECK (slot_minutes BETWEEN 5 AND 240),
    -- Schedule disappears together with its doctor
    CONSTRAINT fk_sched_doctor FOREIGN KEY (doctor_id)
        REFERENCES doctors (doctor_id) ON DELETE CASCADE
);

CREATE TABLE appointments (
    appointment_id   NUMBER GENERATED ALWAYS AS IDENTITY,
    patient_id       NUMBER       NOT NULL,
    doctor_id        NUMBER       NOT NULL,
    appointment_date DATE         NOT NULL,
    slot_time        VARCHAR2(5)  NOT NULL,   -- 'HH24:MI'
    status           VARCHAR2(10) DEFAULT 'BOOKED' NOT NULL,
    created_at       TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_appointments PRIMARY KEY (appointment_id),
    CONSTRAINT ck_appt_status CHECK (status IN ('BOOKED','CANCELLED','COMPLETED')),
    CONSTRAINT ck_appt_date   CHECK (appointment_date = TRUNC(appointment_date)),
    CONSTRAINT ck_appt_slot   CHECK (REGEXP_LIKE(slot_time, '^([01][0-9]|2[0-3]):[0-5][0-9]$')),
    -- Erasing a patient erases their appointments
    CONSTRAINT fk_appt_patient FOREIGN KEY (patient_id)
        REFERENCES patients (patient_id) ON DELETE CASCADE,
    -- A doctor with history cannot be deleted; set is_active = 'N' instead
    CONSTRAINT fk_appt_doctor FOREIGN KEY (doctor_id)
        REFERENCES doctors (doctor_id)
);

CREATE INDEX ix_appt_patient ON appointments (patient_id, appointment_date);
CREATE INDEX ix_appt_doctor  ON appointments (doctor_id, appointment_date);
CREATE INDEX ix_sched_doctor ON doctor_schedules (doctor_id, day_of_week);

-- Declarative double-booking guards: uniqueness applies to BOOKED rows only,
-- so a cancelled slot becomes free again. (All-NULL keys are not indexed.)
CREATE UNIQUE INDEX uq_appt_doctor_slot ON appointments (
    CASE WHEN status = 'BOOKED' THEN doctor_id END,
    CASE WHEN status = 'BOOKED' THEN appointment_date END,
    CASE WHEN status = 'BOOKED' THEN slot_time END);

CREATE UNIQUE INDEX uq_appt_patient_slot ON appointments (
    CASE WHEN status = 'BOOKED' THEN patient_id END,
    CASE WHEN status = 'BOOKED' THEN appointment_date END,
    CASE WHEN status = 'BOOKED' THEN slot_time END);

-- Last line of defence (also covers direct INSERTs that bypass BookAppointment):
-- slot must lie on the doctor's schedule grid and must not be in the past.
CREATE OR REPLACE TRIGGER trg_appt_validate
BEFORE INSERT OR UPDATE OF doctor_id, appointment_date, slot_time, status ON appointments
FOR EACH ROW
WHEN (NEW.status = 'BOOKED')
DECLARE
    v_cnt NUMBER;
BEGIN
    IF TO_DATE(TO_CHAR(:NEW.appointment_date, 'YYYY-MM-DD') || ' ' || :NEW.slot_time,
               'YYYY-MM-DD HH24:MI') < SYSDATE THEN
        RAISE_APPLICATION_ERROR(-20006, 'Appointments cannot be booked in the past.');
    END IF;

    SELECT COUNT(*) INTO v_cnt
      FROM doctor_schedules s
     WHERE s.doctor_id   = :NEW.doctor_id
       AND s.day_of_week = TRUNC(:NEW.appointment_date) - TRUNC(:NEW.appointment_date, 'IW') + 1
       AND :NEW.slot_time >= s.start_time
       AND :NEW.slot_time <  s.end_time
       AND MOD(ROUND((TO_DATE(:NEW.slot_time, 'HH24:MI') - TO_DATE(s.start_time, 'HH24:MI')) * 1440),
               s.slot_minutes) = 0;

    IF v_cnt = 0 THEN
        RAISE_APPLICATION_ERROR(-20003,
            'The doctor does not work at the selected date/time.');
    END IF;
END;
/
