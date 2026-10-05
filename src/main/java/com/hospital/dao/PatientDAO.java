package com.hospital.dao;

import com.hospital.db.DataAccessException;
import com.hospital.db.Database;
import com.hospital.model.Patient;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public final class PatientDAO {
    private static final String INSERT =
            "INSERT INTO patients (first_name, last_name, date_of_birth, phone, email) VALUES (?,?,?,?,?)";
    private static final String SEARCH =
            "SELECT patient_id, first_name, last_name, date_of_birth, phone, email FROM patients "
          + "WHERE phone LIKE ? OR UPPER(last_name) LIKE ? OR UPPER(first_name) LIKE ? "
          + "ORDER BY last_name, first_name FETCH FIRST 50 ROWS ONLY";

    /** Inserts the patient and returns it with the generated identity value. */
    public Patient create(Patient p) throws DataAccessException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT, new String[] {"PATIENT_ID"})) {
            ps.setString(1, p.firstName());
            ps.setString(2, p.lastName());
            ps.setDate(3, Date.valueOf(p.dateOfBirth()));
            ps.setString(4, p.phone());
            if (p.email() == null) ps.setNull(5, Types.VARCHAR); else ps.setString(5, p.email());
            ps.executeUpdate();
            int id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated key returned.");
                id = keys.getInt(1);
            }
            return new Patient(id, p.firstName(), p.lastName(), p.dateOfBirth(), p.phone(), p.email());
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }

    /** Case-insensitive lookup by part of first name, last name or phone number. */
    public List<Patient> search(String term) throws DataAccessException {
        String t = term == null ? "" : term.trim().toUpperCase().replace("%", "").replace("_", "");
        String like = "%" + t + "%";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SEARCH)) {
            ps.setString(1, like);
            ps.setString(2, like);
            ps.setString(3, like);
            try (ResultSet rs = ps.executeQuery()) {
                List<Patient> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Patient(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getDate(4).toLocalDate(), rs.getString(5), rs.getString(6)));
                }
                return out;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
