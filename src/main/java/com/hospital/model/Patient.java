package com.hospital.model;

import java.time.LocalDate;

public record Patient(int id, String firstName, String lastName,
                      LocalDate dateOfBirth, String phone, String email) {
    public String fullName() { return firstName + " " + lastName; }
    @Override public String toString() { return fullName() + " (" + phone + ")"; }
}
