package com.hospital.util;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/** Stateless input validators. Each returns the cleaned value or throws ValidationException. */
public final class Validation {
    private static final Pattern NAME = Pattern.compile("^\\p{L}[\\p{L} .'-]{0,49}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private Validation() { }

    public static String name(String label, String v) {
        String s = v == null ? "" : v.trim();
        if (!NAME.matcher(s).matches())
            throw new ValidationException(label + " is required (letters, spaces, . ' - ; max 50 characters).");
        return s;
    }

    /** Strips spaces, dashes and brackets; accepts 7-15 digits with optional leading '+'. */
    public static String phone(String v) {
        String s = v == null ? "" : v.trim().replaceAll("[ ()-]", "");
        if (!PHONE.matcher(s).matches())
            throw new ValidationException("Phone must contain 7-15 digits (optional leading +).");
        return s;
    }

    /** Returns null when blank. */
    public static String optionalEmail(String v) {
        String s = v == null ? "" : v.trim();
        if (s.isEmpty()) return null;
        if (s.length() > 120 || !EMAIL.matcher(s).matches())
            throw new ValidationException("Email address is not valid.");
        return s;
    }

    public static LocalDate dateOfBirth(String v) {
        LocalDate d = parseDate("Date of birth", v);
        if (d.isAfter(LocalDate.now()) || d.isBefore(LocalDate.of(1900, 1, 1)))
            throw new ValidationException("Date of birth must be between 1900-01-01 and today.");
        return d;
    }

    /** Appointment/search date: yyyy-MM-dd, today or later. */
    public static LocalDate futureDate(String label, String v) {
        LocalDate d = parseDate(label, v);
        if (d.isBefore(LocalDate.now()))
            throw new ValidationException(label + " cannot be in the past.");
        return d;
    }

    /** Returns null when blank, otherwise as futureDate. */
    public static LocalDate optionalFutureDate(String label, String v) {
        return (v == null || v.isBlank()) ? null : futureDate(label, v);
    }

    private static LocalDate parseDate(String label, String v) {
        try {
            return LocalDate.parse(v == null ? "" : v.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException(label + " must be a valid date in yyyy-MM-dd format.");
        }
    }
}
