package com.hospital.gui;

import com.hospital.model.Doctor;
import com.hospital.model.Patient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Shared UI state (EDT only): active patient and the doctor/date handed from search to booking. */
public final class Session {
    private Patient patient;
    private Doctor doctor;
    private LocalDate date;
    private final List<Runnable> listeners = new ArrayList<>();

    public Patient patient() { return patient; }
    public Doctor doctor() { return doctor; }
    public LocalDate date() { return date; }

    public void setPatient(Patient p) { this.patient = p; fire(); }
    public void setBookingTarget(Doctor d, LocalDate dt) { this.doctor = d; this.date = dt; fire(); }
    public void onChange(Runnable r) { listeners.add(r); }

    private void fire() { listeners.forEach(Runnable::run); }
}
