# 🚂 IRCTC Rail Connect — Railway Reservation System (IRCTC-Style UI)

**JavaFX + Heavy CSS** desktop application built as a modern practice project in the Coding Practice workspace.

IRCTC-style professional blue-white-orange redesign of the main search experience (hero form, station codes in parens "Name (CODE)", swap, return date toggle, class/quota, flexible/avail filters, prominent search) while fully preserving all existing functionality: real 65-train timetable data, station code resolution (codes/names/partials), H2 DB bookings, dynamic per-class interactive seat maps, payment simulation, PNR receipts, admin, etc.

A fully functional, visually polished train ticket booking system with interactive seat maps, PNR generation, persistence via JSON, search, bookings, and admin management. (Updated 2026-05-24 for IRCTC UI v2)

## ✨ Features (MVP v1)

- **Search Trains** by source/destination + date
- **Beautiful train cards** with availability pills (heavy glassmorphism CSS)
- **Interactive booking dialog**:
  - Class selector (SL / 3A / 2A / 1A) with live styling
  - Passenger count selector (1–6)
  - **Visual seat map** (40 seats) — click to select exactly the required number (CSS powered hover/selected states)
  - Dynamic passenger details form (name, age, gender, berth preference)
  - Live fare calculation with class multipliers
- **PNR generation** and beautiful confirmation screen with big styled PNR display
- **My Bookings** tab with list of all bookings (filtered by current user), cancel functionality (restores seats)
- **Manage Trains** admin tab — add/update custom trains + reload sample data
- **Login / Register** — proper username + password authentication (BCrypt + optional MySQL backend)
- **Full persistence**: trains + bookings saved to `~/.railway-reservation/` (JSON, survives restarts)
- **Heavy CSS**: 200+ lines of custom rules — navy + saffron IRCTC-inspired theme, frosted cards, animated buttons, realistic seat grid, focus/hover/pressed states, modern inputs
- **Real Timetable Data (Phase 2)**: 65+ authentic trains from official IRCTC-style PDF timetables (Delhi–Eastern India corridor). Source: restructured extractions from 5 detailed PDF timetables (2026 session). Includes Rajdhani, Poorva, Mahabodhi, Vande Bharat, Duronto, Humsafar, Garib Rath, Jan Sadharan, Vibhuti, etc. with real train numbers, classes, days of operation, and full station schedules. The `data/parse_railway_timetables.py` helper + `raw-timetables.json` are provided for future updates.
- **Payment Gateway (Phase 3)**: Full simulated IRCTC-style payment with Card / UPI / Net Banking / Wallet. Test cards: `4242...` = success, `4000...` = failure. Payment details are stored and shown in My Bookings + beautiful receipt screen.
- No external DB or server required

## 🛠 Tech Stack

- Java 21
- JavaFX 21 (pure code, no FXML)
- Jackson 2.17 (JSON)
- Maven + javafx-maven-plugin + shade plugin

## ▶️ How to Run

```bash
cd RailwayReservationSystem

# First time (downloads JavaFX modules)
mvn clean javafx:run

# Subsequent runs
mvn javafx:run
```

After first successful run, data is created in:
`~/.railway-reservation/trains.json` and `bookings.json`

## 🔐 Authentication & MySQL (User Passwords)

The app now supports **real username + password login** (replacing the old passwordless user picker).

- Click **👤 Login** in the header → use the "Login or Register" dialog.
- **Default admin**: `admin` / `admin`
- New users can self-register (password ≥ 4 characters, stored with BCrypt).

**MySQL Support (Users + Bookings)**

Both **user accounts (with BCrypt passwords)** and **all bookings** can now be stored in MySQL instead of the embedded H2 database.

### Steps:
1. Create your MySQL database:
   ```sql
   CREATE DATABASE RailwayReservation CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. Set these environment variables **before** running the app:

   ```bash
   export MYSQL_URL="jdbc:mysql://127.0.0.1:3306/RailwayReservation?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   export MYSQL_USER="root"
   export MYSQL_PASSWORD="lunapnb1"
   ```

3. (Optional but recommended) Create the tables manually (the app will also create them automatically):
   ```sql
   USE RailwayReservation;
   -- (paste the users + bookings table SQL from the project or let the app auto-create)
   ```

4. Run the application:
   ```bash
   mvn clean javafx:run
   ```

When `MYSQL_URL` is set, the app automatically uses MySQL via HikariCP for both `users` and `bookings` tables.  
When not set, it falls back to the local H2 file database (previous default behavior).

**Note:** The default admin account (`admin` / `admin`) is created automatically on first run if it doesn't exist.

## 📦 Build Fat Jar (optional)

```bash
mvn clean package
java -jar target/railway-reservation-system-1.0.jar
```

## 📁 Project Structure

```
RailwayReservationSystem/
├── pom.xml
├── .gitignore
├── README.md
├── data/
│   ├── parse_railway_timetables.py   # Heuristic parser for PDF timetable extractions
│   └── raw-timetables.json           # Source data from 5 official IR PDFs (Delhi-Howrah corridor)
└── src/main/
    ├── java/com/railwayreservation/
    │   ├── RailwayApp.java
    │   ├── model/ (Train, Booking, Passenger, ScheduleStop, User)
    │   ├── service/ (DataService, UserRepository, BookingRepository)
    │   └── util/ (PNRGenerator)
    └── resources/
        ├── data/real-trains.json     # Cleaned 65+ real trains (loaded at runtime)
        └── styles/railway-reservation.css
```

## 🎨 CSS Highlights

- Deep navy background (`#0a192f`) + saffron accent (`#ff9933`)
- Glassmorphism cards with layered gradients + heavy dropshadows
- Fully interactive seat grid with distinct available/selected/booked states + scale animations
- Press/hover scale effects on all primary actions
- Monospace PNR hero display
- Tab, form, list, and dialog polish

## 🧪 Sample / Real Data

**Phase 2**: 28+ real trains from official extracted timetables (Delhi–Eastern India corridor):
- Rajdhani (12301/12302, 12306, 12310, 12314...), Poorva, Mahabodhi (12397/12398), Vande Bharat (22436), Duronto, Garib Rath, Humsafar, Vibhuti, Magadh, Vikramshila, Shatabdi, etc.
- Authentic frequencies (Daily, M.Th.Sa, Tu.W.F.Su...)
- Rich per-class availability
- Full station list for the corridor (New Delhi, Anand Vihar (T), Pt. Deen Dayal Upadhyaya Jn, Gaya, Howrah, Sealdah...)

Fallback to 8 generic samples only if the real JSON is missing.

## ✅ Verification Checklist (after `mvn clean javafx:run`)

1. App launches with beautiful dark UI and header
2. Search Delhi → Mumbai → several train cards appear
3. Open Book → pick class, passengers=2, select exactly 2 seats in grid, fill details → fare updates live
4. Confirm → PNR shown in styled ticket dialog, data saved
5. Go to My Bookings → see entry + Cancel works (seats restored)
6. Restart app → previous bookings & seat counts persist
7. Admin tab → add a new train → it appears in search
8. Book a ticket → "Proceed to Pay" opens full payment gateway (Card/UPI/NetBanking/Wallet)
9. Use test card `4242 4242 4242 4242` → success + receipt + PNR. Use `4000...` to test failure.
10. My Bookings now shows payment method + transaction ID
11. `mvn clean compile` succeeds cleanly

## 🔮 Future Enhancements (out of v1 scope)

- Full MySQL for bookings + trains (currently only users/passwords table uses optional MySQL)
- FXML + controllers
- QR code on ticket + PDF export
- Live availability across users
- Password reset / admin user management UI

---

Built with ❤️ for coding practice — demonstrating strong JavaFX UI skills + heavy CSS craftsmanship.

**Version**: 1.0 • 2026

## ✨ UI/UX v2 Enhancements (Completed per detailed plan)
- Professional header with logo, 4 main tabs (Search/My Bookings/Live/Admin), avatar + context menu (Profile/Settings/Logout)
- Enhanced left sidebar navigation
- Search: autocomplete filtering on stations, popular route chips, flexible dates wired, nearby stub, improved cards with duration + running days dots
- Booking: coach selector, berth type legend, ladies/senior seat highlights, copy previous passenger, fare breakdown (base+GST+resv)
- Theming: Dark/Light/Classic + high-contrast toggle in Settings
- My Bookings: status badges, per-ticket QR canvas
- Payment: real drawn fake QR canvas
- Admin: search filter + simple visual occupancy bars
- Polish: keyboard shortcuts (Ctrl+K search), more tooltips, ScrollPane responsive, micro-animations enhanced, spinners/labels
- A11y: high-contrast mode, better labels

All changes in single file + CSS. `mvn clean compile` clean.
