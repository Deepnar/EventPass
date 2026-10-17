# EventPass Setup — College Laptop (Windows + MySQL Workbench)

Exact, copy-paste checklist. No Docker needed. You need Java 21, Maven 3.9+, MySQL Server + Workbench.

## One-time installs
| Tool | Get it | Verify |
|---|---|---|
| Java 21 | https://adoptium.net/ (Temurin 21 LTS `.msi`) | `java -version` |
| Maven 3.9+ | https://maven.apache.org/download.cgi (unzip to `C:\Program Files\Maven`) | `mvn -v` |
| MySQL + Workbench | https://dev.mysql.com/downloads/installer/ (remember root password) | Workbench connects to localhost:3306 |

If `java`/`mvn` aren't recognised, open a **new** cmd window.

## Step 1 — Create the database
In MySQL Workbench, run:
```sql
CREATE DATABASE eventpass;
```
No tables needed — Hibernate creates them on first boot. To wipe later:
```sql
DROP DATABASE eventpass;
CREATE DATABASE eventpass;
```

## Step 2 — Get the project
```cmd
cd C:\Users\<YourName>\Projects
```
Copy the `EventPass` folder here (USB/drive), then:
```cmd
cd EventPass\rapidrail
```

## Step 3 — Set your MySQL password (one line)
Option A (recommended) — set env var in cmd before running:
```cmd
set DB_PASSWORD=your_mysql_root_password
```
Option B — edit `src\main\resources\application.properties`:
```properties
spring.datasource.password=YOUR_MYSQL_WORKBENCH_PASSWORD
```
(Defaults assume host `localhost:3306`, user `root`, database `eventpass`.)

## Step 4 — Run
```cmd
mvn spring-boot:run
```
Wait for `Tomcat started on port 8081` / `Started EventPassApplication`. First boot takes 30–60 s.

## Step 5 — Open the app
- Attendee: `http://localhost:8081` — register (₹500 bonus), browse, book.
- Organizer: `http://localhost:8081/organizer.html` — sign in with a seeded organizer:
  - `aarav@rhythmnights.in` / `organizer123`, or
  - `meera@skillHum.in` / `organizer123`
  or register a new organizer on that page.

## Viva demo flow
1. Organizer panel → Events → + Add Event (concert, price, capacity).
2. Attendee tab → register → book `2` tickets → see 2 QR codes.
3. Organizer → Check-In Scan → paste ticket 1 → Check In → ✅ VALID.
4. Same token again → ⚠️ ALREADY USED. Ticket 2 → ✅ VALID independently.
5. My Bookings → Cancel a fresh booking → seats + refund restored.
6. Paste token under another event → 🚫 WRONG EVENT; garbage → ❌ INVALID.

## If something breaks
- "Communications link failure" / "Access denied": MySQL80 service not running (`services.msc`), wrong password, or wrong port.
- "Unknown database 'eventpass'": run `CREATE DATABASE eventpass;`.
- "Port 8081 already in use": set `server.port=8082` in `application.properties`.
- Slow first run: normal — Maven caches JARs in `C:\Users\<You>\.m2\` afterwards.
