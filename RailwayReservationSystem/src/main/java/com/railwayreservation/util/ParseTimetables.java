package com.railwayreservation.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParseTimetables {

    private static String cleanName(String s) {
        if (s == null) return null;
        String out = s.replaceAll("\\s+", " ").trim();
        out = out.replace("  ", " ");
        out = out.replaceAll("-\\s+", "-");
        return out;
    }

    public static List<Map<String, Object>> parseTrains(JsonNode raw) {
        List<Map<String, Object>> trains = new ArrayList<>();

        JsonNode documents = raw.path("documents");
        if (!documents.isArray()) return trains;

        Pattern trainNumPattern = Pattern.compile("Train Number\\s+([0-9]+)");

        for (JsonNode doc : documents) {
            for (JsonNode page : doc.path("pages")) {
                String content = page.path("content").asText("");
                if (content.isEmpty()) continue;

                Matcher m = trainNumPattern.matcher(content);
                List<String> found = new ArrayList<>();
                while (m.find()) {
                    found.add(m.group(1));
                }
                if (found.isEmpty()) continue;

                // limit per page for demo
                int limit = Math.min(found.size(), 3);
                for (int i = 0; i < limit; i++) {
                    String tn = found.get(i);
                    Map<String, Object> train = new LinkedHashMap<>();
                    train.put("trainNo", tn);

                    // Heuristic name extraction: look for the train number followed by a name-like token
                    String name = null;
                    try {
                        // regex: <tn> <Name...> (stopping before next number or keywords)
                        Pattern namePat = Pattern.compile(Pattern.quote(tn) + "\\s+([A-Za-z][A-Za-z\\s\\.-]+?)(?:\\s+\\d{1,6}|Train Number|Class|$)", Pattern.CASE_INSENSITIVE);
                        Matcher nm = namePat.matcher(content);
                        if (nm.find()) {
                            name = cleanName(nm.group(1));
                        }
                    } catch (Exception ignored) {}

                    if (name == null || name.isEmpty()) name = "Train " + tn;
                    train.put("name", name.length() > 60 ? name.substring(0, 60) : name);
                    train.put("source", "New Delhi");
                    train.put("destination", "Howrah");
                    train.put("departure", "00.00");
                    train.put("arrival", "00.00");

                    Map<String, Integer> seats = new LinkedHashMap<>();
                    seats.put("SL", 120);
                    seats.put("3A", 48);
                    seats.put("2A", 24);
                    seats.put("1A", 12);
                    train.put("availableSeats", seats);

                    train.put("baseFare", 1450);
                    train.put("frequency", "Daily");
                    train.put("classes", "1A,2A,3A,SL");

                    List<Map<String, String>> schedule = new ArrayList<>();
                    Map<String, String> s1 = new LinkedHashMap<>();
                    s1.put("station", "New Delhi");
                    s1.put("time", "17:00");
                    Map<String, String> s2 = new LinkedHashMap<>();
                    s2.put("station", "Howrah");
                    s2.put("time", "08:35");
                    schedule.add(s1);
                    schedule.add(s2);
                    train.put("schedule", schedule);

                    trains.add(train);
                }
            }
        }

        // Deduplicate by trainNo
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> t : trains) {
            unique.putIfAbsent((String) t.get("trainNo"), t);
        }

        return new ArrayList<>(unique.values());
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -cp <jar> com.railwayreservation.util.ParseTimetables raw-timetables.json");
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode raw = mapper.readTree(new File(args[0]));
        List<Map<String, Object>> result = parseTrains(raw);

        // Print pretty JSON to stdout
        mapper.writerWithDefaultPrettyPrinter().writeValue(System.out, result);
    }
}
