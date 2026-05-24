package com.railwayreservation.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Passenger {
    private String name;
    private int age;
    private String gender;
    private String berthPref;
    private String seatNumber;

    public Passenger() {}

    public Passenger(String name, int age, String gender, String berthPref) {
        this.name = name;
        this.age = age;
        this.gender = gender;
        this.berthPref = berthPref;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBerthPref() { return berthPref; }
    public void setBerthPref(String berthPref) { this.berthPref = berthPref; }

    public String getSeatNumber() { return seatNumber; }
    public void setSeatNumber(String seatNumber) { this.seatNumber = seatNumber; }

    public boolean isSeniorCitizen() { return age >= 60; }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (name == null || name.isBlank()) errors.add("Name is required");
        if (age < 1 || age > 120) errors.add("Age must be 1–120");
        if (gender == null || gender.isBlank()) errors.add("Gender is required");
        return errors;
    }

    @Override
    public String toString() {
        String seat = (seatNumber != null && !seatNumber.isBlank()) ? ", seat: " + seatNumber : "";
        return name + " (" + age + ", " + gender + (berthPref != null ? ", " + berthPref : "") + seat + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Passenger)) return false;
        Passenger that = (Passenger) o;
        return age == that.age && Objects.equals(name, that.name) && Objects.equals(gender, that.gender) && Objects.equals(berthPref, that.berthPref) && Objects.equals(seatNumber, that.seatNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age, gender, berthPref, seatNumber);
    }
}