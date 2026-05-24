package com.railwayreservation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.railwayreservation.model.Booking;
import com.railwayreservation.model.Passenger;
import com.railwayreservation.model.Train;
import com.railwayreservation.model.ScheduleStop;
import com.railwayreservation.util.PNRGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DataService {

    private static final Path DATA_DIR = Paths.get(System.getProperty("user.home"), ".railway-reservation");
    private static final Path TRAINS_FILE = DATA_DIR.resolve("trains.json");

    private final ObjectMapper mapper;
    private List<Train> trains = new ArrayList<>();
    private List<Booking> bookings = new ArrayList<>();
    private final BookingRepository bookingRepository;

    private static final Map<String, Double> CLASS_MULTIPLIERS = Map.ofEntries(
        Map.entry("SL", 1.0),
        Map.entry("3A", 1.8),
        Map.entry("2A", 2.5),
        Map.entry("1A", 3.2),
        Map.entry("3E", 1.5),
        Map.entry("EC", 2.8),
        Map.entry("CC", 2.0),
        Map.entry("P", 1.2),
        Map.entry("SLIIP", 1.1)
    );

    private static final List<String> VALID_CLASSES = List.of("1A", "2A", "3A", "3E", "SL", "SLIIP", "EC", "CC", "P");

    /** Master corridor stations compiled from real timetable extractions (Delhi-Howrah/Patna corridor) */
    private static final List<String> MASTER_STATIONS = List.of(
        "Delhi", "New Delhi", "Anand Vihar (T)", "Ghaziabad", "Khurja", "Aligarh", "Mathura", "Agra Cantt.",
        "Tundla", "Firozabad", "Shikohabad", "Etawah", "Kanpur", "Fatehpur", "Unchahar", "Prayag",
        "Prayagraj", "Prayagraj Rambag", "Prayagghat", "Varanasi", "Mirzapur", "Pt. Deen Dayal Upadhyaya Jn",
        "Sasaram", "Dehri-on-Sone", "Gaya", "Parasnath", "Netaji Subhash Chandra Bose Gomoh", "Dhanbad",
        "Buxar", "Ara", "Danapur", "Patliputra Jn.", "Patna", "Rajendranagar", "Rajendranagar (T)",
        "Mokama", "Kiul", "Jhajha", "Jasidih", "Madhupur", "Chittaranjan", "Asansol", "Durgapur",
        "Jamalpur", "Bhagalpur", "Godda", "Barddhaman", "Bandel", "Dankuni", "Kolkata", "Kolkata Shalimar",
        "Sealdah", "Howrah"
    );

    private static final Map<String, String> CODE_TO_NAME = new LinkedHashMap<>();
    private static final Map<String, String> NAME_TO_CODE = new HashMap<>();

    static {
        addStation("NDLS", "New Delhi");
        addStation("DLI", "Delhi");
        addStation("ANVT", "Anand Vihar (T)");
        addStation("GZB", "Ghaziabad");
        addStation("KRJ", "Khurja");
        addStation("ALJN", "Aligarh");
        addStation("MTJ", "Mathura");
        addStation("AGC", "Agra Cantt.");
        addStation("TDL", "Tundla");
        addStation("FZD", "Firozabad");
        addStation("SKB", "Shikohabad");
        addStation("ETW", "Etawah");
        addStation("CNB", "Kanpur");
        addStation("FTP", "Fatehpur");
        addStation("PRYJ", "Prayagraj");
        addStation("BSB", "Varanasi");
        addStation("MZP", "Mirzapur");
        addStation("DDU", "Pt. Deen Dayal Upadhyaya Jn");
        addStation("SSM", "Sasaram");
        addStation("DOS", "Dehri-on-Sone");
        addStation("GAYA", "Gaya");
        addStation("DHN", "Dhanbad");
        addStation("BXR", "Buxar");
        addStation("ARA", "Ara");
        addStation("DNR", "Danapur");
        addStation("PNBE", "Patna");
        addStation("MKA", "Mokama");
        addStation("KIUL", "Kiul");
        addStation("JAJ", "Jhajha");
        addStation("JSME", "Jasidih");
        addStation("MDP", "Madhupur");
        addStation("CRJ", "Chittaranjan");
        addStation("ASN", "Asansol");
        addStation("DGR", "Durgapur");
        addStation("JMP", "Jamalpur");
        addStation("BGP", "Bhagalpur");
        addStation("BWN", "Barddhaman");
        addStation("HWH", "Howrah");
        addStation("SDAH", "Sealdah");
        addStation("KOAA", "Kolkata");
    }

    private static void addStation(String code, String name) {
        CODE_TO_NAME.put(code, name);
        NAME_TO_CODE.put(name, code);
    }

    public DataService() {
        this.mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ensureDataDir();
        this.bookingRepository = new BookingRepository();
        migrateOldBookingsIfNeeded();
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(DATA_DIR);
        } catch (IOException e) {
            System.err.println("Warning: Could not create data dir " + DATA_DIR);
        }
    }

    public void load() {
        try {
            List<Train> realTrains = loadRealTrainsFromResource();
            List<Train> userTrains = null;
            if (Files.exists(TRAINS_FILE)) {
                try {
                    userTrains = mapper.readValue(TRAINS_FILE.toFile(), new TypeReference<List<Train>>() {});
                } catch (Exception ex) {
                    System.err.println("Failed to read persisted trains: " + ex.getMessage());
                }
            }
            if (!realTrains.isEmpty()) {
                if (userTrains != null && !userTrains.isEmpty()) {
                    Map<String, Train> userMap = new HashMap<>();
                    for (Train u : userTrains) {
                        if (u.getTrainNo() != null) userMap.put(u.getTrainNo(), u);
                    }
                    for (Train r : realTrains) {
                        Train u = userMap.get(r.getTrainNo());
                        if (u != null && u.getAvailableSeats() != null && !u.getAvailableSeats().isEmpty()) {
                            r.setAvailableSeats(new LinkedHashMap<>(u.getAvailableSeats()));
                        }
                    }
                }
                trains = realTrains;
            } else if (userTrains != null && !userTrains.isEmpty()) {
                trains = userTrains;
            } else {
                initSampleTrains();
            }
            if (trains.isEmpty()) {
                initSampleTrains();
            }
            saveTrains();

            // Load bookings from database (H2)
            bookings = bookingRepository.findAll();
        } catch (Exception e) {
            System.err.println("Error loading data, falling back to samples: " + e.getMessage());
            initSampleTrains();
            bookings = new ArrayList<>();
            saveTrains();
        }
    }

    private List<Train> loadRealTrainsFromResource() {
        try (InputStream is = getClass().getResourceAsStream("/data/real-trains.json")) {
            if (is == null) {
                return Collections.emptyList();
            }
            List<Train> list = mapper.readValue(is, new TypeReference<List<Train>>() {});
            normalizeTrains(list);
            return list;
        } catch (Exception e) {
            System.err.println("Could not load real-trains.json from classpath: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private void normalizeTrains(List<Train> list) {
        for (Train t : list) {
            if (t.getAvailableSeats() == null || t.getAvailableSeats().isEmpty()) {
                Map<String, Integer> seats = new LinkedHashMap<>();
                String clsStr = t.getClasses();
                if (clsStr != null && !clsStr.isBlank()) {
                    for (String c : clsStr.split("[,\\s]+")) {
                        c = c.trim().toUpperCase().replace("II", "SL");
                        if (!c.isEmpty()) {
                            seats.putIfAbsent(c, 60);
                        }
                    }
                }
                if (seats.isEmpty()) {
                    seats.put("SL", 120);
                    seats.put("3A", 50);
                    seats.put("2A", 30);
                    seats.put("1A", 10);
                }
                t.setAvailableSeats(seats);
            }
            if (t.getSource() == null || t.getSource().isBlank()) {
                t.setSource("TBD");
                t.setDestination("TBD");
                t.setDeparture("--:--");
                t.setArrival("--:--");
            }
            if (t.getBaseFare() <= 0) {
                t.setBaseFare(1200);
            }
            if (t.getFrequency() == null || t.getFrequency().isBlank()) {
                t.setFrequency("Various");
            }
            if (t.getSchedule() == null) {
                t.setSchedule(new ArrayList<>());
            }
        }
    }

    public void saveTrains() {
        try {
            mapper.writeValue(TRAINS_FILE.toFile(), trains);
        } catch (IOException e) {
            System.err.println("Failed to save trains: " + e.getMessage());
        }
    }

    public void saveBookings() {
        // Persist all bookings to H2 database
        for (Booking b : bookings) {
            bookingRepository.save(b);
        }
    }

    public void saveAll() {
        saveTrains();
        saveBookings();
    }

    private void initSampleTrains() {
        trains = new ArrayList<>();

        // Sample 1
        Map<String, Integer> seats1 = new LinkedHashMap<>();
        seats1.put("SL", 120); seats1.put("3A", 48); seats1.put("2A", 24); seats1.put("1A", 12);
        trains.add(new Train("12301", "Rajdhani Express", "New Delhi", "Mumbai Central", "17:00", "08:35", seats1, 2500));

        // Sample 2
        Map<String, Integer> seats2 = new LinkedHashMap<>();
        seats2.put("SL", 180); seats2.put("3A", 72); seats2.put("2A", 36); seats2.put("1A", 18);
        trains.add(new Train("12951", "Mumbai Rajdhani", "Mumbai Central", "New Delhi", "16:30", "08:05", seats2, 2450));

        // Sample 3
        Map<String, Integer> seats3 = new LinkedHashMap<>();
        seats3.put("SL", 90); seats3.put("3A", 40); seats3.put("2A", 20); seats3.put("1A", 8);
        trains.add(new Train("22691", "Shatabdi Express", "New Delhi", "Chandigarh", "07:00", "11:10", seats3, 850));

        // Sample 4
        Map<String, Integer> seats4 = new LinkedHashMap<>();
        seats4.put("SL", 200); seats4.put("3A", 80); seats4.put("2A", 40); seats4.put("1A", 20);
        trains.add(new Train("12627", "Karnataka Express", "New Delhi", "Bengaluru", "21:40", "06:00", seats4, 1850));

        // Sample 5
        Map<String, Integer> seats5 = new LinkedHashMap<>();
        seats5.put("SL", 150); seats5.put("3A", 60); seats5.put("2A", 30); seats5.put("1A", 15);
        trains.add(new Train("12295", "Sanghamitra SF Express", "Bengaluru", "New Delhi", "13:50", "05:30", seats5, 1920));

        // Sample 6
        Map<String, Integer> seats6 = new LinkedHashMap<>();
        seats6.put("SL", 110); seats6.put("3A", 44); seats6.put("2A", 22); seats6.put("1A", 10);
        trains.add(new Train("12839", "Howrah Mail", "Kolkata", "Chennai Central", "23:55", "04:10", seats6, 1650));

        // Sample 7
        Map<String, Integer> seats7 = new LinkedHashMap<>();
        seats7.put("SL", 140); seats7.put("3A", 56); seats7.put("2A", 28); seats7.put("1A", 14);
        trains.add(new Train("12723", "Telangana Express", "Hyderabad", "New Delhi", "06:25", "05:50", seats7, 1780));

        // Sample 8
        Map<String, Integer> seats8 = new LinkedHashMap<>();
        seats8.put("SL", 95); seats8.put("3A", 38); seats8.put("2A", 18); seats8.put("1A", 6);
        trains.add(new Train("12009", "Shatabdi Express", "Chennai Central", "Mumbai Central", "06:00", "21:30", seats8, 1420));

        System.out.println("Initialized " + trains.size() + " sample trains.");
    }

    public List<Train> getAllTrains() {
        return new ArrayList<>(trains);
    }

    public List<String> getAllStations() {
        if (trains.isEmpty()) {
            return MASTER_STATIONS.stream()
                .map(this::formatStationDisplay)
                .collect(Collectors.toList());
        }
        Set<String> stations = new TreeSet<>();
        for (Train t : trains) {
            if (t.getSource() != null && !t.getSource().isBlank() && !"TBD".equalsIgnoreCase(t.getSource())) stations.add(t.getSource());
            if (t.getDestination() != null && !t.getDestination().isBlank() && !"TBD".equalsIgnoreCase(t.getDestination())) stations.add(t.getDestination());
            if (t.getSchedule() != null) {
                for (ScheduleStop s : t.getSchedule()) {
                    if (s.getStation() != null && !s.getStation().isBlank()) stations.add(s.getStation());
                }
            }
        }
        if (stations.isEmpty()) {
            return MASTER_STATIONS.stream()
                .map(this::formatStationDisplay)
                .collect(Collectors.toList());
        }
        return stations.stream()
            .map(this::formatStationDisplay)
            .collect(Collectors.toList());
    }

    private String formatStationDisplay(String station) {
        String code = NAME_TO_CODE.get(station);
        return (code != null) ? code + " - " + station : station;
    }

    public List<Train> searchTrains(String from, String to, LocalDate date) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            return new ArrayList<>();
        }
        String f = resolveStation(from).toLowerCase();
        String t = resolveStation(to).toLowerCase();

        return trains.stream()
            .filter(tr -> matchesRoute(tr, f, t))
            .sorted(Comparator.comparing(Train::getDeparture, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    private boolean matchesRoute(Train tr, String f, String t) {
        String src = tr.getSource() != null ? tr.getSource().toLowerCase() : "";
        String dst = tr.getDestination() != null ? tr.getDestination().toLowerCase() : "";
        if (src.contains(f) && dst.contains(t)) return true;
        List<ScheduleStop> sched = tr.getSchedule();
        if (sched == null || sched.size() < 2) return false;
        int fromIdx = -1;
        int toIdx = -1;
        for (int i = 0; i < sched.size(); i++) {
            String st = sched.get(i).getStation();
            if (st == null) continue;
            String sl = st.toLowerCase();
            if (fromIdx < 0 && sl.contains(f)) fromIdx = i;
            if (sl.contains(t)) toIdx = i;
        }
        return fromIdx >= 0 && toIdx > fromIdx;
    }

    private String resolveStation(String input) {
        if (input == null || input.isBlank()) return "";
        String trimmed = input.trim();

        if (trimmed.contains(" - ")) {
            String[] parts = trimmed.split(" - ", 2);
            if (parts.length == 2) {
                String possibleCode = parts[0].trim().toUpperCase();
                if (CODE_TO_NAME.containsKey(possibleCode)) {
                    return CODE_TO_NAME.get(possibleCode);
                }
                return parts[1].trim();
            }
        }

        String upper = trimmed.toUpperCase();
        if (CODE_TO_NAME.containsKey(upper)) {
            return CODE_TO_NAME.get(upper);
        }

        for (String name : NAME_TO_CODE.keySet()) {
            if (name.equalsIgnoreCase(trimmed)) {
                return name;
            }
        }

        return trimmed;
    }

    public double calculateFare(Train train, String cls, int numPassengers) {
        double mult = CLASS_MULTIPLIERS.getOrDefault(cls, 1.0);
        return Math.round(train.getBaseFare() * mult * numPassengers * 100.0) / 100.0;
    }

    public List<String> getValidClasses() {
        return VALID_CLASSES;
    }

    public boolean bookTicket(Train train, String cls, int numSeats, List<Passenger> passengers, String userName, String journeyDate,
                              String paymentMethod, String transactionId) {
        if (!VALID_CLASSES.contains(cls) || !train.hasAvailability(cls, numSeats) || passengers.size() != numSeats) {
            return false;
        }

        // Snapshot current train state
        train.decrementSeats(cls, numSeats);
        saveTrains(); // persist availability immediately

        Booking booking = new Booking();
        booking.setPnr(PNRGenerator.generate());
        booking.setUserName(userName != null ? userName : "Guest");
        booking.setTrainNo(train.getTrainNo());
        booking.setTrainName(train.getName());
        booking.setJourneyDate(journeyDate != null ? journeyDate : LocalDate.now().plusDays(1).toString());
        booking.setCls(cls);
        booking.setPassengers(new ArrayList<>(passengers));
        booking.setTotalFare(calculateFare(train, cls, numSeats));
        booking.setBookedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        booking.setPaymentMethod(paymentMethod);
        booking.setTransactionId(transactionId);
        booking.setPaymentStatus("SUCCESS");

        bookings.add(booking);
        saveBookings();

        return true;
    }

    public boolean cancelBooking(String pnr) {
        Optional<Booking> opt = bookings.stream().filter(b -> b.getPnr().equals(pnr)).findFirst();
        if (opt.isEmpty()) return false;

        Booking b = opt.get();
        // Find matching train and restore seats
        trains.stream()
            .filter(tr -> tr.getTrainNo().equals(b.getTrainNo()))
            .findFirst()
            .ifPresent(tr -> tr.incrementSeats(b.getCls(), b.getPassengers().size()));

        bookings.remove(b);
        saveAll();
        return true;
    }

    public List<Booking> getAllBookings() {
        return new ArrayList<>(bookings);
    }

    public List<Booking> getBookingsForUser(String userName) {
        if (userName == null || userName.isBlank()) {
            return getAllBookings();
        }
        String u = userName.trim();
        return bookings.stream()
            .filter(b -> b.getUserName().equalsIgnoreCase(u))
            .collect(Collectors.toList());
    }

    // Admin helpers
    public void addOrUpdateTrain(Train train) {
        int idx = -1;
        for (int i = 0; i < trains.size(); i++) {
            if (trains.get(i).getTrainNo().equals(train.getTrainNo())) {
                idx = i; break;
            }
        }
        if (idx >= 0) {
            trains.set(idx, train);
        } else {
            trains.add(train);
        }
        saveTrains();
    }

    public void reloadSamples() {
        List<Train> real = loadRealTrainsFromResource();
        if (!real.isEmpty()) {
            trains = real;
        } else {
            initSampleTrains();
        }
        saveTrains();
    }

    public List<Train> getTrains() {
        return trains;
    }

    private void migrateOldBookingsIfNeeded() {
        Path oldBookings = DATA_DIR.resolve("bookings.json");
        if (Files.exists(oldBookings) && bookingRepository.findAll().isEmpty()) {
            try {
                List<Booking> old = mapper.readValue(oldBookings.toFile(), new TypeReference<List<Booking>>() {});
                for (Booking b : old) {
                    bookingRepository.save(b);
                }
                System.out.println("Migrated " + old.size() + " bookings from JSON to database.");
                // Optional: rename the old file
                Files.move(oldBookings, DATA_DIR.resolve("bookings.json.bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Booking migration failed: " + e.getMessage());
            }
        }
    }
}
