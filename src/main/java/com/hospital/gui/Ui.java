package com.hospital.gui;

import com.hospital.db.DataAccessException;
import com.hospital.util.ValidationException;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Small Swing helpers shared by all views. */
public final class Ui {
    private static final Logger LOG = Logger.getLogger(Ui.class.getName());

    private Ui() { }

    public static JTable readOnlyTable(String... columns) {
        DefaultTableModel m = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(m);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);
        t.setRowHeight(24);
        return t;
    }

    public static DefaultTableModel model(JTable t) { return (DefaultTableModel) t.getModel(); }

    /** Selected row translated to model index (rows may be sorted), or -1. */
    public static int selectedRow(JTable t) {
        int r = t.getSelectedRow();
        return r < 0 ? -1 : t.convertRowIndexToModel(r);
    }

    public static void addRow(JPanel form, int row, String label, JComponent field) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = row; g.insets = new Insets(4, 4, 4, 4); g.anchor = GridBagConstraints.WEST;
        g.gridx = 0; form.add(new JLabel(label), g);
        g.gridx = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; form.add(field, g);
    }

    public static void info(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Information", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String msg) {
        JOptionPane.showMessageDialog(parent, msg, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String msg) {
        return JOptionPane.showConfirmDialog(parent, msg, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Central error presentation: validation, Oracle business rules, technical failures. */
    public static void showError(Component parent, Throwable t) {
        if (t instanceof ValidationException) {
            warn(parent, t.getMessage());
        } else if (t instanceof DataAccessException dae) {
            if (dae.isBusinessRuleViolation()) {
                JOptionPane.showMessageDialog(parent, dae.getUserMessage(),
                        "Cannot complete request", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent, dae.getUserMessage(),
                        "Database error", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            LOG.log(Level.SEVERE, "Unexpected error", t);
            JOptionPane.showMessageDialog(parent, "Unexpected error: " + t,
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
