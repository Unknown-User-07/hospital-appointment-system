package com.hospital;

import com.hospital.db.Database;
import com.hospital.gui.Async;
import com.hospital.gui.MainFrame;

import javax.swing.*;

public final class HospitalApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { /* fall back to default look and feel */ }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            // Fail fast, with a friendly message, if the database is unreachable.
            Async.run(frame, () -> { try (var c = Database.getConnection()) { return c.isValid(5); } }, ok -> { });
        });
    }
}
