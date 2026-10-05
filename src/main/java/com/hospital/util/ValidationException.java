package com.hospital.util;

/** Thrown when user input fails validation; message is shown to the user as-is. */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) { super(message); }
}
