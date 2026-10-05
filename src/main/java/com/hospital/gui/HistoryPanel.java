package com.hospital.gui;

import com.hospital.dao.AppointmentDAO;
import com.hospital.model.AppointmentView;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** View 4: appointment history of the active patient (via GetPatientHistory) with cancel support. */
public final class HistoryPanel extends JPanel implements Refreshable {
    private final AppointmentDAO dao = new AppointmentDAO();
    private final Session session;
    private final JLabel header = new JLabel();
    private final JTable table = Ui.readOnlyTable("ID", "Date", "Time", "Doctor", "Specialization", "Status");
    private final List<AppointmentView> rows = new ArrayList<>();

    public HistoryPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton refresh = new JButton("Refresh");
        JButton cancel = new JButton("Cancel selected appointment");
        refresh.addActionListener(e -> refresh());
        cancel.addActionListener(e -> cancelSelected());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(refresh);
        south.add(cancel);
        add(south, BorderLayout.SOUTH);
        session.onChange(() -> { if (isShowing()) refresh(); });
    }

    @Override public void refresh() {
        if (session.patient() == null) {
            header.setText("No patient selected - choose one on the Patient tab.");
            Ui.model(table).setRowCount(0);
            rows.clear();
            return;
        }
        final int pid = session.patient().id();
        header.setText("Appointment history: " + session.patient());
        Async.run(this, () -> dao.history(pid), list -> {
            rows.clear();
            rows.addAll(list);
            Ui.model(table).setRowCount(0);
            for (AppointmentView a : list) {
                Ui.model(table).addRow(new Object[] {a.id(), a.date(), a.slot(),
                        "Dr. " + a.doctorName(), a.specialization(), a.status()});
            }
        });
    }

    private void cancelSelected() {
        if (session.patient() == null) { Ui.warn(this, "No patient selected."); return; }
        int r = Ui.selectedRow(table);
        if (r < 0) { Ui.warn(this, "Select an appointment first."); return; }
        AppointmentView a = rows.get(r);
        if (!"BOOKED".equals(a.status())) { Ui.warn(this, "Only BOOKED appointments can be cancelled."); return; }
        if (!Ui.confirm(this, "Cancel appointment #" + a.id() + " on " + a.date() + " at " + a.slot() + "?")) return;
        final int pid = session.patient().id();
        Async.run(this, () -> { dao.cancel(pid, a.id()); return Boolean.TRUE; }, ok -> {
            Ui.info(this, "Appointment cancelled.");
            refresh();
        }, this::refresh);
    }
}
