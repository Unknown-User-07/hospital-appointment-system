package com.hospital.dao;

import com.hospital.db.DataAccessException;
import com.hospital.db.Database;
import com.hospital.model.Specialization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class SpecializationDAO {
    private static final String SQL_ALL = "SELECT specialization_id, name FROM specializations ORDER BY name";

    public List<Specialization> findAll() throws DataAccessException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SQL_ALL);
             ResultSet rs = ps.executeQuery()) {
            List<Specialization> out = new ArrayList<>();
            while (rs.next()) out.add(new Specialization(rs.getInt(1), rs.getString(2)));
            return out;
        } catch (SQLException e) {
            throw DataAccessException.from(e);
        }
    }
}
