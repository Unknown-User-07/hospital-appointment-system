package com.hospital.gui;

import com.hospital.dao.DoctorDAO;
import com.hospital.dao.SpecializationDAO;
import com.hospital.model.Doctor;
import com.hospital.model.Specialization;
import com.hospital.util.Validation;
import com.hospital.util.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** View 2: find doctors by specialization and/or working date. */
public final class DoctorSearchPanel extends JPanel implements Refreshable {
    private static final Specialization ALL = new Specialization(0, "All specializations");

    private final DoctorDAO doctorDao = new DoctorDAO();
    private final SpecializationDAO specDao = new SpecializationDAO();
    private final Session session;
    private final Runnable goToBooking;

    private final JComboBox<Specialization> specCombo = new JComboBox<>();
    private final JTextField dateField = new JTextField(10);
    private final JTable table = Ui.readOnlyTable("Doctor", "Specialization", "Hours on that day", "Email", "Phone");
    private final List<Doctor> rows = new ArrayList<>();
    private LocalDate searchedDate;

    public DoctorSearchPanel(Session session, Runnable goToBooking) {
        this.session = session;
        this.goToBooking = goToBooking;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.setBorder(BorderFactory.createTitledBorder("Filters"));
        specCombo.addItem(ALL);
        bar.add(new JLabel("Specialization:"));
        bar.add(specCombo);
        bar.add(new JLabel("Date (yyyy-MM-dd, optional):"));
        bar.add(dateField);
        JButton search = new JButton("Search");
        search.addActionListener(e -> search());
        bar.add(search);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JButton book = new JButton("Book with selected doctor \u2192");
        book.addActionListener(e -> bookSelected());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(book);
        add(south, BorderLayout.SOUTH);
    }

    @Override public void refresh() {
        if (specCombo.getItemCount() <= 1) loadSpecializations();
    }

    private void loadSpecializations() {
        Async.run(this, specDao::findAll, list -> {
            if (specCombo.getItemCount() > 1) return;
            list.forEach(specCombo::addItem);
        });
    }

    private void search() {
        final LocalDate date;
        try {
            date = Validation.optionalFutureDate("Date", dateField.getText());
        } catch (ValidationException ex) {
            Ui.warn(this, ex.getMessage());
            return;
        }
        Specialization sel = (Specialization) specCombo.getSelectedItem();
        Integer specId = (sel == null || sel.id() == 0) ? null : sel.id();
        Async.run(this, () -> doctorDao.search(specId, date), list -> {
            searchedDate = date;
            rows.clear();
            rows.addAll(list);
            Ui.model(table).setRowCount(0);
            for (Doctor d : list) {
                Ui.model(table).addRow(new Object[] {"Dr. " + d.fullName(), d.specialization(),
                        d.hours() == null ? "-" : d.hours(), d.email(), d.phone() == null ? "" : d.phone()});
            }
            if (list.isEmpty()) Ui.info(this, "No doctors match the selected filters.");
        });
    }

    private void bookSelected() {
        int r = Ui.selectedRow(table);
        if (r < 0) { Ui.warn(this, "Select a doctor from the table first."); return; }
        session.setBookingTarget(rows.get(r), searchedDate);
        goToBooking.run();
    }
}
