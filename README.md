# Hospital Appointment Booking System
Java 17 Swing desktop client + JDBC + Oracle Database 12c or later (identity columns, `FETCH FIRST`).

## 1. Project structure
```
sql/01_schema.sql        tables, constraints, indexes, validation trigger
sql/02_procedures.sql    BookAppointment, GetPatientHistory, GetAvailableSlots, CancelAppointment
sql/03_seed.sql          sample specializations, doctors, schedules, one patient
config/db.properties     connection settings
src/main/java/com/hospital/
  HospitalApp.java       entry point
  model/                 immutable records (Patient, Doctor, Specialization, AppointmentView)
  db/                    Database (connections), DataAccessException (ORA-xxxxx -> user message)
  dao/                   PatientDAO, DoctorDAO, SpecializationDAO, AppointmentDAO
  util/                  Validation, ValidationException
  gui/                   MainFrame, PatientPanel, DoctorSearchPanel, BookingPanel, HistoryPanel,
                         Session (shared state), Async (SwingWorker helper), Ui (helpers)
```

## 2. Setup guide
1. **Create the schema owner** (as SYSDBA / in your PDB):
   ```sql
   CREATE USER hospital IDENTIFIED BY "change_me" QUOTA UNLIMITED ON users;
   GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE, CREATE TRIGGER TO hospital;
   ```
2. **Run the scripts** connected as `hospital`, in order: `01_schema.sql`, `02_procedures.sql`, `03_seed.sql`
   (SQL Developer: open file -> *Run Script* F5; SQL*Plus: `@sql/01_schema.sql`).
3. **Get the JDBC driver**: download `ojdbc8.jar` from
   <https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html> and put it in `lib/`
   (Maven users: the dependency `com.oracle.database.jdbc:ojdbc8` is already in `pom.xml`).
4. **Configure** `config/db.properties` (`db.url`, `db.user`, `db.password`).
   URL formats: service name `jdbc:oracle:thin:@//host:1521/XEPDB1`, SID `jdbc:oracle:thin:@host:1521:XE`.
   Keep the password out of files with the `DB_PASSWORD` environment variable, and point to another
   file with `-Ddb.config=/path/db.properties`.
5. **Run** (JDK 17+):
   - Maven: `mvn compile exec:java`
   - Without Maven: `./build-and-run.sh` (Windows: `build-and-run.bat`)
   - Manually: `javac -d out $(find src/main/java -name "*.java")` then
     `java -cp "out:lib/ojdbc8.jar" com.hospital.HospitalApp` (use `;` as separator on Windows).

Typical flow: **Patient** tab (register or search, select) -> **Find Doctor** (filter, "Book with selected doctor")
-> **Book Appointment** (show free slots, book) -> **History** (review / cancel).
Seed data: Mon-Fri 09:00-13:00 for all doctors; pick a future weekday.

Troubleshooting: `ORA-12514` = wrong service name; `ORA-01017` = bad credentials;
`ORA-28000` = account locked; `ClassNotFound/No suitable driver` = `ojdbc8.jar` not on the classpath.

## 3. Normalization (3NF)

| Entity | 1NF | 2NF | 3NF |
|---|---|---|---|
| **SPECIALIZATIONS** (`specialization_id` PK, `name` UQ) | All columns atomic, no repeating groups. | Single-column PK, so no partial dependencies are possible. | Only one non-key attribute, so no transitive dependency. Extracting it removes the repeated specialization text that would otherwise sit in every doctor row. |
| **DOCTORS** (`doctor_id` PK, names, email UQ, phone, `specialization_id` FK, `is_active`) | Atomic columns; first/last name split; no phone lists. | Single-column surrogate PK, so every attribute depends on the whole key. | `doctor -> specialization_id -> specialization name` would be transitive, so only the FK is stored and the name lives in SPECIALIZATIONS. Schedule rows (a repeating group) live in their own table. |
| **PATIENTS** (`patient_id` PK, names, `date_of_birth`, phone UQ, email, `created_at`) | Atomic columns. | Single-column PK. | Age is not stored (derivable from `date_of_birth`, so it would depend on a non-key attribute and go stale). No attribute determines another non-key attribute. |
| **DOCTOR_SCHEDULES** (`schedule_id` PK, `doctor_id` FK, `day_of_week`, `start_time`, `end_time`, `slot_minutes`; candidate key `(doctor_id, day_of_week, start_time)`) | One availability window per row; multiple windows per day are rows, not lists. | Every non-key column describes the whole window. No doctor attributes are copied here. | Doctor name/specialization are reached via the FK; `end_time` and `slot_minutes` depend only on the window, not on each other. |
| **APPOINTMENTS** (`appointment_id` PK, `patient_id` FK, `doctor_id` FK, `appointment_date`, `slot_time`, `status`, `created_at`) | Date and slot are separate atomic columns. | Surrogate PK; all attributes describe this appointment. | No patient or doctor names, no specialization, no duration (that comes from the schedule). The natural key `(doctor, date, slot)` for BOOKED rows is enforced by a filtered unique index, so cancelled slots can be re-booked. |

**ON DELETE rules**: `schedules -> doctors` CASCADE (a schedule is meaningless without its doctor);
`appointments -> patients` CASCADE (erasure of a patient's data);
`appointments -> doctors` and `doctors -> specializations` NO ACTION (history is protected; deactivate doctors with `is_active='N'`).

## 4. Conflict handling and concurrency
Defence in depth, from friendliest to strictest:
1. **`GetAvailableSlots`** only offers free slots, so most conflicts never reach the user.
2. **`BookAppointment`** takes `SELECT ... FOR UPDATE WAIT 10` row locks on the doctor, then the patient
   (always in that order, so no deadlocks). Two clients booking the same doctor are serialized: the second one
   sees the first one's committed row and receives `ORA-20004` ("slot just taken"). A patient double-booking at
   the same time with another doctor gives `ORA-20005`. A lock wait timeout gives `ORA-20008`.
3. **Unique function-based indexes** (`uq_appt_doctor_slot`, `uq_appt_patient_slot`) make double booking impossible
   even for direct SQL; `DUP_VAL_ON_INDEX` is mapped to the same friendly error.
4. **`trg_appt_validate`** rejects past times (`-20006`) and times that are not on the doctor's slot grid (`-20003`).

The DAO sets `autoCommit(false)`, commits after the call (which releases the locks) and rolls back on any `SQLException`.
`DataAccessException` converts `ORA-20xxx` into a clean message; the GUI shows rule violations as warning pop-ups
and technical failures as error pop-ups. All JDBC work runs on `SwingWorker` threads, so the UI never freezes.

## 5. Extension notes
- Replace `DriverManager` in `Database` with Oracle UCP / HikariCP for connection pooling.
- Store hashed credentials / use a wallet instead of a plain-text password for real deployments.
- Past-date and phone/e-mail checks exist both in `Validation` (UX) and the database (integrity).
