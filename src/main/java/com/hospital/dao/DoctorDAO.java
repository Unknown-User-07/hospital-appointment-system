package com.hospital.dao;

import com.hospital.db.DataAccessException;
import com.hospital.db.Database;
import com.hospital.model.Doctor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class DoctorDAO {

    /**
     * Active doctors, optionally filtered by specialization and by "works on this date's weekday".
     * With a date the result also carries that day's working hours.
     * Dynamic parts are fixed SQL fragments; all values are bound parameters.
     */
    public List<Doctor> search(Integer specializationId, LocalDate date) throws DataAccessException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT d.doctor_id, d.first_name || ' ' || d.last_name, sp.name, d.email, d.phone, ");
        if (date != null) {
            sql.append("(SELECT LISTAGG(s.start_time || '-' || s.end_time, ', ') WITHIN GROUP (ORDER BY s.start_time) ")
               .append("   FROM doctor_schedules s WHERE s.doctor_id = d.doctor_id AND s.day_of_week = ?) AS hours ");
            params.add(date.getDayOfWeek().getValue());      // ISO: Monday = 1
        } else {
            sql.append("CAST(NULL AS VARCHAR2(100)) AS hours ");
        }
        sql.append("FROM doctors d JOIN specializations sp ON sp.specialization_id = d.specialization_id ")
           .append("WHERE d.is_active = 'Y' ");
        if (specializationId != null) {
            sql.append("AND d.specialization_id = ? ");
            params.add(specializationId);
        }
        if (date != null) {
            sql.append("AND EXISTS (SELECT 1 FROM doctor_schedules s2 WHERE s2.doctor_id = d.doctor_id AND s2.day_of_week = ?) ");
            params.add(date.getDayOfWeek().getValue());
        }
        sql.append("ORDER BY d.last_name, d.first_name");

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setInt(i + 1, (Integer) params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                List<Doctor> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new Doctor(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6)));
                }
                return out;
            }
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
