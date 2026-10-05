package com.hospital.model;

/** A medical specialization. toString() makes it directly usable in a JComboBox. */
public record Specialization(int id, String name) {
    @Override public String toString() { return name; }
}
