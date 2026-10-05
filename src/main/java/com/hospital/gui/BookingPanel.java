package com.hospital.gui;

import com.hospital.dao.AppointmentDAO;
import com.hospital.dao.DoctorDAO;
import com.hospital.model.Doctor;
import com.hospital.util.Validation;
import com.hospital.util.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/** View 3: choose doctor + date, load free slots and book. Conflicts are reported by Oracle. */
public final class BookingPanel extends JPanel implements Refreshable {
    private final AppointmentDAO appointmentDao = new AppointmentDAO();
    private final DoctorDAO doctorDao = new DoctorDAO();
    private final Session session;

    private final JLabel patientLabel = new JLabel();
    private final JComboBox<Doctor> doctorCombo = new JComboBox<>();
    private final JTextField dateField = new JTextField(10);
    private final JComboBox<String> slotCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final JButton loadSlots = new JButton("Show free slots");
    private final JButton bookBtn = new JButton("Book appointment");

    public BookingPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("New appointment"));
        Ui.addRow(form, 0, "Patient", patientLabel);
        Ui.addRow(form, 1, "Doctor *", doctorCombo);
        Ui.addRow(form, 2, "Date * (yyyy-MM-dd)", dateField);
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 3; g.gridx = 1; g.anchor = GridBagConstraints.WEST; g.insets = new Insets(4, 4, 4, 4);
        form.add(loadSlots, g);
        Ui.addRow(form, 4, "Free slot *", slotCombo);
        g.gridy = 5; g.gridx = 1; form.add(bookBtn, g);
        g.gridy = 6; form.add(status, g);

        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.setPreferredSize(new Dimension(520, 280));
        wrap.add(form);
        add(wrap, BorderLayout.NORTH);

        loadSlots.addActionListener(e -> loadSlots());
        bookBtn.addActionListener(e -> book());
        doctorCombo.addActionListener(e -> slotCombo.removeAllItems());
        session.onChange(this::syncFromSession);
        syncFromSession();
    }

    @Override public void refresh() {
        syncFromSession();
        if (doctorCombo.getItemCount() == 0) loadDoctors();
    }

    private void syncFromSession() {
        patientLabel.setText(session.patient() == null
                ? "(none - select one on the Patient tab)" : session.patient().toString());
        Doctor target = session.doctor();
        if (target != null) {
            ensureDoctor(target);
            doctorCombo.setSelectedItem(find(target.id()));
            if (session.date() != null) dateField.setText(session.date().toString());
            slotCombo.removeAllItems();
        }
    }

    private void ensureDoctor(Doctor d) {
        if (find(d.id()) == null) doctorCombo.addItem(d);
    }

    private Doctor find(int id) {
        for (int i = 0; i < doctorCombo.getItemCount(); i++) {
            if (doctorCombo.getItemAt(i).id() == id) return doctorCombo.getItemAt(i);
        }
        return null;
    }

    private void loadDoctors() {
        Async.run(this, () -> doctorDao.search(null, null), list -> {
            Doctor keep = (Doctor) doctorCombo.getSelectedItem();
            doctorCombo.removeAllItems();
            list.forEach(doctorCombo::addItem);
            if (keep != null) doctorCombo.setSelectedItem(find(keep.id()));
        });
    }

    private void loadSlots() {
        final Doctor doctor = (Doctor) doctorCombo.getSelectedItem();
        final LocalDate date;
        try {
            if (doctor == null) throw new ValidationException("Select a doctor.");
            date = Validation.futureDate("Date", dateField.getText());
        } catch (ValidationException ex) {
            Ui.warn(this, ex.getMessage());
            return;
        }
        status.setText("Loading slots...");
        Async.run(this, () -> appointmentDao.availableSlots(doctor.id(), date), slots -> {
            slotCombo.removeAllItems();
            slots.forEach(slotCombo::addItem);
            status.setText(slots.isEmpty() ? "No free slots on " + date + "." : slots.size() + " free slot(s).");
        }, () -> status.setText(" "));
    }

    private void book() {
        final Doctor doctor = (Doctor) doctorCombo.getSelectedItem();
        final String slot = (String) slotCombo.getSelectedItem();
        final LocalDate date;
        try {
            if (session.patient() == null) throw new ValidationException("Select or register a patient first.");
            if (doctor == null) throw new ValidationException("Select a doctor.");
            date = Validation.futureDate("Date", dateField.getText());
            if (slot == null) throw new ValidationException("Load the free slots and choose one.");
        } catch (ValidationException ex) {
            Ui.warn(this, ex.getMessage());
            return;
        }
        final int patientId = session.patient().id();
        if (!Ui.confirm(this, "Book " + session.patient().fullName() + " with Dr. " + doctor.fullName()
                + "\non " + date + " at " + slot + "?")) return;

        bookBtn.setEnabled(false);
        Async.run(this, () -> appointmentDao.book(patientId, doctor.id(), date, slot), id -> {
            bookBtn.setEnabled(true);
            Ui.info(this, "Appointment #" + id + " confirmed for " + date + " at " + slot + ".");
            loadSlots();
        }, () -> {                       // Oracle rejected it (e.g. slot taken): refresh the list
            bookBtn.setEnabled(true);
            loadSlots();
        });
    }
}
