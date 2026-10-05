package com.hospital.dao;

import com.hospital.db.DataAccessException;
import com.hospital.db.Database;
import com.hospital.model.AppointmentView;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All appointment operations go through the PL/SQL procedures; the DAO owns the transaction. */
public final class AppointmentDAO {

    /** Books a slot atomically. Conflicts surface as DataAccessException (ORA-20xxx). Returns the new id. */
    public int book(int patientId, int doctorId, LocalDate date, String slot) throws DataAccessException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (CallableStatement cs = c.prepareCall("{call BookAppointment(?,?,?,?,?)}")) {
                cs.setInt(1, patientId);
                cs.setInt(2, doctorId);
                cs.setDate(3, Date.valueOf(date));
                cs.setString(4, slot);
                cs.registerOutParameter(5, Types.NUMERIC);
                cs.execute();
                int id = cs.getInt(5);
                c.commit();               // releases the FOR UPDATE row locks
                return id;
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw DataAccessException.from(e);
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    public List<String> availableSlots(int doctorId, LocalDate date) throws DataAccessException {
        try (Connection c = Database.getConnection();
             CallableStatement cs = c.prepareCall("{call GetAvailableSlots(?,?,?)}")) {
            cs.setInt(1, doctorId);
            cs.setDate(2, Date.valueOf(date));
            cs.registerOutParameter(3, Types.REF_CURSOR);
            cs.execute();
            List<String> out = new ArrayList<>();
            try (ResultSet rs = cs.getObject(3, ResultSet.class)) {
                while (rs.next()) out.add(rs.getString(1));
            }
            return out;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    public List<AppointmentView> history(int patientId) throws DataAccessException {
        try (Connection c = Database.getConnection();
             CallableStatement cs = c.prepareCall("{call GetPatientHistory(?,?)}")) {
            cs.setInt(1, patientId);
            cs.registerOutParameter(2, Types.REF_CURSOR);
            cs.execute();
            List<AppointmentView> out = new ArrayList<>();
            try (ResultSet rs = cs.getObject(2, ResultSet.class)) {
                while (rs.next()) {
                    out.add(new AppointmentView(rs.getInt("appointment_id"),
                            rs.getDate("appointment_date").toLocalDate(),
                            rs.getString("slot_time"),
                            rs.getString("doctor_name"),
                            rs.getString("specialization"),
                            rs.getString("status")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    public void cancel(int patientId, int appointmentId) throws DataAccessException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try (CallableStatement cs = c.prepareCall("{call CancelAppointment(?,?)}")) {
                cs.setInt(1, patientId);
                cs.setInt(2, appointmentId);
                cs.execute();
                c.commit();
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw DataAccessException.from(e);
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try { c.rollback(); } catch (SQLException ignored) { /* connection is closed next anyway */ }
    }
}
