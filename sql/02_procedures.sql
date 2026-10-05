-- =====================================================================
-- Stored procedures. Custom error codes:
--   -20001 patient not found        -20005 patient already booked then
--   -20002 doctor not found         -20006 date/time in the past (trigger)
--   -20003 outside doctor schedule  -20007 appointment not cancellable
--   -20004 doctor already booked    -20008 system busy (lock timeout)
-- =====================================================================

CREATE OR REPLACE PROCEDURE BookAppointment (
    p_patient_id       IN  NUMBER,
    p_doctor_id        IN  NUMBER,
    p_appointment_date IN  DATE,
    p_slot_time        IN  VARCHAR2,
    p_appointment_id   OUT NUMBER
) AS
    v_date    DATE := TRUNC(p_appointment_date);
    v_lock_id NUMBER;
    v_cnt     NUMBER;
    e_timeout EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_timeout, -30006);   -- resource busy / WAIT expired
BEGIN
    -- Lock order is ALWAYS doctor -> patient, so sessions can never deadlock.
    -- Locking the doctor row serialises all concurrent bookings for that doctor.
    BEGIN
        SELECT doctor_id INTO v_lock_id
          FROM doctors
         WHERE doctor_id = p_doctor_id AND is_active = 'Y'
           FOR UPDATE WAIT 10;
    EXCEPTION WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20002, 'Doctor does not exist or is inactive.');
    END;

    BEGIN
        SELECT patient_id INTO v_lock_id
          FROM patients
         WHERE patient_id = p_patient_id
           FOR UPDATE WAIT 10;
    EXCEPTION WHEN NO_DATA_FOUND THEN
        RAISE_APPLICATION_ERROR(-20001, 'Patient does not exist.');
    END;

    -- Safe checks: nobody else can book this doctor/patient until we commit.
    SELECT COUNT(*) INTO v_cnt FROM appointments
     WHERE doctor_id = p_doctor_id AND appointment_date = v_date
       AND slot_time = p_slot_time AND status = 'BOOKED';
    IF v_cnt > 0 THEN
        RAISE_APPLICATION_ERROR(-20004,
            'This time slot has just been taken. Please choose another slot.');
    END IF;

    SELECT COUNT(*) INTO v_cnt FROM appointments
     WHERE patient_id = p_patient_id AND appointment_date = v_date
       AND slot_time = p_slot_time AND status = 'BOOKED';
    IF v_cnt > 0 THEN
        RAISE_APPLICATION_ERROR(-20005,
            'The patient already has another appointment at this date and time.');
    END IF;

    INSERT INTO appointments (patient_id, doctor_id, appointment_date, slot_time)
    VALUES (p_patient_id, p_doctor_id, v_date, p_slot_time)
    RETURNING appointment_id INTO p_appointment_id;
    -- No COMMIT here: the caller (JDBC DAO) owns the transaction.
EXCEPTION
    WHEN DUP_VAL_ON_INDEX THEN   -- backstop: unique indexes
        RAISE_APPLICATION_ERROR(-20004,
            'This time slot has just been taken. Please choose another slot.');
    WHEN e_timeout THEN
        RAISE_APPLICATION_ERROR(-20008, 'The system is busy. Please try again.');
END BookAppointment;
/

CREATE OR REPLACE PROCEDURE GetPatientHistory (
    p_patient_id IN  NUMBER,
    p_cursor     OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_cursor FOR
        SELECT a.appointment_id,
               a.appointment_date,
               a.slot_time,
               d.first_name || ' ' || d.last_name AS doctor_name,
               sp.name                            AS specialization,
               a.status
          FROM appointments a
          JOIN doctors d          ON d.doctor_id = a.doctor_id
          JOIN specializations sp ON sp.specialization_id = d.specialization_id
         WHERE a.patient_id = p_patient_id
         ORDER BY a.appointment_date DESC, a.slot_time DESC;
END GetPatientHistory;
/

-- Free slots for a doctor on a date, generated from the weekly template.
CREATE OR REPLACE PROCEDURE GetAvailableSlots (
    p_doctor_id IN  NUMBER,
    p_date      IN  DATE,
    p_cursor    OUT SYS_REFCURSOR
) AS
BEGIN
    OPEN p_cursor FOR
        SELECT slot_time FROM (
            SELECT DISTINCT
                   TO_CHAR(TO_DATE(s.start_time, 'HH24:MI')
                           + NUMTODSINTERVAL(g.n * s.slot_minutes, 'MINUTE'), 'HH24:MI') AS slot_time
              FROM doctor_schedules s
              JOIN (SELECT LEVEL - 1 AS n FROM dual CONNECT BY LEVEL <= 288) g
                ON g.n < FLOOR(ROUND((TO_DATE(s.end_time, 'HH24:MI')
                                    - TO_DATE(s.start_time, 'HH24:MI')) * 1440) / s.slot_minutes)
             WHERE s.doctor_id   = p_doctor_id
               AND s.day_of_week = TRUNC(p_date) - TRUNC(p_date, 'IW') + 1
        ) x
         WHERE NOT EXISTS (SELECT 1 FROM appointments a
                            WHERE a.doctor_id = p_doctor_id
                              AND a.appointment_date = TRUNC(p_date)
                              AND a.slot_time = x.slot_time
                              AND a.status = 'BOOKED')
           AND (TRUNC(p_date) > TRUNC(SYSDATE)
                OR (TRUNC(p_date) = TRUNC(SYSDATE) AND x.slot_time > TO_CHAR(SYSDATE, 'HH24:MI')))
         ORDER BY slot_time;
END GetAvailableSlots;
/

CREATE OR REPLACE PROCEDURE CancelAppointment (
    p_patient_id     IN NUMBER,
    p_appointment_id IN NUMBER
) AS
BEGIN
    UPDATE appointments
       SET status = 'CANCELLED'
     WHERE appointment_id = p_appointment_id
       AND patient_id     = p_patient_id
       AND status         = 'BOOKED';
    IF SQL%ROWCOUNT = 0 THEN
        RAISE_APPLICATION_ERROR(-20007, 'Appointment not found or cannot be cancelled.');
    END IF;
END CancelAppointment;
/
