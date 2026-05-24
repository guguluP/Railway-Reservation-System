package com.railwayreservation.model;

import java.util.Objects;

public class ScheduleStop {
    private String station;
    private String time;
    private int day = 1;             // Day 1, Day 2 for overnight trains
    private String haltMinutes;      // "2 min" | "Origin" | "Destination"
    private int distanceKm;         // km from origin

    public ScheduleStop() {}

    public ScheduleStop(String station, String time) {
        this.station = station;
        this.time = time;
    }

    public String getStation() { return station; }
    public void setStation(String station) { this.station = station; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }

    public String getHaltMinutes() { return haltMinutes; }
    public void setHaltMinutes(String haltMinutes) { this.haltMinutes = haltMinutes; }

    public int getDistanceKm() { return distanceKm; }
    public void setDistanceKm(int distanceKm) { this.distanceKm = distanceKm; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduleStop)) return false;
        ScheduleStop s = (ScheduleStop) o;
        return day == s.day && Objects.equals(station, s.station);
    }

    @Override
    public int hashCode() { return Objects.hash(station, day); }

    @Override
    public String toString() {
        String dayStr = day > 1 ? " (Day " + day + ")" : "";
        String halt = haltMinutes != null ? " [" + haltMinutes + "]" : "";
        return station + dayStr + "   " + time + halt;
    }
}
