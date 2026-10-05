package com.hospital.gui;

import javax.swing.*;
import java.awt.*;

/** Main window: one tab per view plus a status bar showing the active patient. */
public final class MainFrame extends JFrame {
    public MainFrame() {
        super("Hospital Appointment Booking System");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        Session session = new Session();
        JTabbedPane tabs = new JTabbedPane();
        BookingPanel booking = new BookingPanel(session);
        DoctorSearchPanel search = new DoctorSearchPanel(session, () -> tabs.setSelectedComponent(booking));
        HistoryPanel history = new HistoryPanel(session);

        tabs.addTab("1. Patient", new PatientPanel(session));
        tabs.addTab("2. Find Doctor", search);
        tabs.addTab("3. Book Appointment", booking);
        tabs.addTab("4. History", history);
        tabs.addChangeListener(e -> {
            Component c = tabs.getSelectedComponent();
            if (c instanceof Refreshable r) r.refresh();
        });

        JLabel statusBar = new JLabel();
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        Runnable updateStatus = () -> statusBar.setText("Active patient: "
                + (session.patient() == null ? "none" : session.patient()));
        session.onChange(updateStatus);
        updateStatus.run();

        add(tabs, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
        setSize(900, 560);
        setMinimumSize(new Dimension(760, 480));
        setLocationRelativeTo(null);
    }
}
