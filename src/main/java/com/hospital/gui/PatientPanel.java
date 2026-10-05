package com.hospital.gui;

import com.hospital.dao.PatientDAO;
import com.hospital.model.Patient;
import com.hospital.util.Validation;
import com.hospital.util.ValidationException;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/** View 1: register a new patient or look one up, then make them the active patient. */
public final class PatientPanel extends JPanel {
    private final PatientDAO dao = new PatientDAO();
    private final Session session;

    private final JTextField first = new JTextField(16), last = new JTextField(16),
            dob = new JTextField(16), phone = new JTextField(16), email = new JTextField(16);
    private final JTextField searchField = new JTextField(20);
    private final JTable table = Ui.readOnlyTable("ID", "First name", "Last name", "Date of birth", "Phone", "Email");
    private final List<Patient> rows = new ArrayList<>();

    public PatientPanel(Session session) {
        this.session = session;
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        add(buildRegistration(), BorderLayout.WEST);
        add(buildLookup(), BorderLayout.CENTER);
    }

    private JPanel buildRegistration() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Register new patient"));
        Ui.addRow(form, 0, "First name *", first);
        Ui.addRow(form, 1, "Last name *", last);
        Ui.addRow(form, 2, "Date of birth * (yyyy-MM-dd)", dob);
        Ui.addRow(form, 3, "Phone *", phone);
        Ui.addRow(form, 4, "Email", email);
        JButton register = new JButton("Register");
        register.addActionListener(e -> register(register));
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 5; g.gridx = 1; g.anchor = GridBagConstraints.EAST; g.insets = new Insets(8, 4, 4, 4);
        form.add(register, g);
        return form;
    }

    private JPanel buildLookup() {
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createTitledBorder("Find existing patient"));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Name or phone:"));
        bar.add(searchField);
        JButton search = new JButton("Search");
        bar.add(search);
        search.addActionListener(e -> search());
        searchField.addActionListener(e -> search());
        p.add(bar, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        JButton use = new JButton("Use selected patient");
        use.addActionListener(e -> useSelected());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) useSelected();
            }
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(use);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    private void register(JButton button) {
        final Patient draft;
        try {
            draft = new Patient(0,
                    Validation.name("First name", first.getText()),
                    Validation.name("Last name", last.getText()),
                    Validation.dateOfBirth(dob.getText()),
                    Validation.phone(phone.getText()),
                    Validation.optionalEmail(email.getText()));
        } catch (ValidationException ex) {
            Ui.warn(this, ex.getMessage());
            return;
        }
        button.setEnabled(false);
        Async.run(this, () -> dao.create(draft), saved -> {
            button.setEnabled(true);
            session.setPatient(saved);
            first.setText(""); last.setText(""); dob.setText(""); phone.setText(""); email.setText("");
            Ui.info(this, "Patient registered (ID " + saved.id() + ") and selected as the active patient.");
        }, () -> button.setEnabled(true));
    }

    private void search() {
        String term = searchField.getText();
        Async.run(this, () -> dao.search(term), list -> {
            rows.clear();
            rows.addAll(list);
            Ui.model(table).setRowCount(0);
            for (Patient p : list) {
                Ui.model(table).addRow(new Object[] {p.id(), p.firstName(), p.lastName(),
                        p.dateOfBirth(), p.phone(), p.email() == null ? "" : p.email()});
            }
            if (list.isEmpty()) Ui.info(this, "No matching patients found.");
        });
    }

    private void useSelected() {
        int r = Ui.selectedRow(table);
        if (r < 0) { Ui.warn(this, "Select a patient from the table first."); return; }
        session.setPatient(rows.get(r));
    }
}
