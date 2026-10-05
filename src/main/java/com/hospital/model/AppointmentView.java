package com.hospital.model;

import java.time.LocalDate;

/** Read model for the history table (one row of GetPatientHistory). */
public record AppointmentView(int id, LocalDate date, String slot,
                              String doctorName, String specialization, String status) { }
