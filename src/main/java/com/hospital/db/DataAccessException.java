package com.hospital.db;

import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Wraps SQLException and carries a message that is safe to show to end users.
 * ORA-20000..20999 (RAISE_APPLICATION_ERROR) are treated as business-rule violations.
 */
public class DataAccessException extends Exception {
    private static final Logger LOG = Logger.getLogger(DataAccessException.class.getName());
    private static final Pattern ORA_PREFIX = Pattern.compile("^ORA-\\d{5}:\\s*");

    private final int oraCode;
    private final String userMessage;

    private DataAccessException(String userMessage, int oraCode, Throwable cause) {
        super(userMessage, cause);
        this.userMessage = userMessage;
        this.oraCode = oraCode;
    }

    public int getOraCode() { return oraCode; }
    public String getUserMessage() { return userMessage; }

    public boolean isBusinessRuleViolation() { return oraCode >= 20000 && oraCode <= 20999; }

    public static DataAccessException from(SQLException e) {
        LOG.log(Level.WARNING, "SQL error " + e.getErrorCode(), e);
        int code = e.getErrorCode();
        String msg;
        if (code >= 20000 && code <= 20999) {
            msg = firstLineWithoutPrefix(e.getMessage());
        } else {
            msg = switch (code) {
                case 1 -> "A record with the same unique value (for example phone number) already exists.";
                case 1017 -> "Database login failed. Check db.user / db.password.";
                case 12514, 12541, 12505, 17002, 17008 ->
                        "Cannot reach the Oracle database. Check that it is running and db.url is correct.";
                case 2291 -> "A referenced record does not exist.";
                case 2292 -> "This record is still referenced by other data and cannot be removed.";
                case 2290 -> "A value violates a database rule (check constraint).";
                default -> "A database error occurred"
                        + (code != 0 ? " (ORA-" + String.format("%05d", code) + ")" : "")
                        + ": " + firstLineWithoutPrefix(e.getMessage());
            };
        }
        return new DataAccessException(msg, code, e);
    }

    private static String firstLineWithoutPrefix(String m) {
        if (m == null) return "Unknown error.";
        String first = m.split("\\R", 2)[0];
        return ORA_PREFIX.matcher(first).replaceFirst("");
    }
}
