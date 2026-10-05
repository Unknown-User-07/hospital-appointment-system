package com.hospital.model;

/** hours is a display string of the working windows for the searched weekday (may be null). */
public record Doctor(int id, String fullName, String specialization,
                     String email, String phone, String hours) {
    @Override public String toString() { return "Dr. " + fullName + " - " + specialization; }
}
