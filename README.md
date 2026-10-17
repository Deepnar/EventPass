# EventPass — Local Event Ticket Booking & QR Entry Validation

A Spring Boot web app for booking local events (concerts, workshops, seminars) with
quantity selection, wallet payments (instant demo top-up + Razorpay test-mode checkout),
per-ticket QR codes, organizer check-in scan simulation, and event recommendations.

**Pages:** attendee home (`/`) → browse/book (`dashboard.html`) → organizer panel (`organizer.html`).

---

## 1. Prerequisites

| Tool | Version | Download | Verify |
|---|---|---|---|
| Java (JDK) | 21 LTS | https://adoptium.net/ (Temurin 21) | `java -version` |
| Maven | 3.9+ | https://maven.apache.org/download.cgi | `mvn -v` |
| Docker **or** local MySQL 8 | any recent / 8.x | https://www.docker.com/ or https://dev.mysql.com/downloads/installer/ | `docker ps` |
| Git | any | https://git-scm.com/downloads | `git --version` |
| DBeaver *(optional, to browse tables)* | any | https://dbeaver.io/download/ | — |

> Windows: after installing Java/Maven, open a **new** terminal before verifying.
> First boot downloads Maven dependencies (~30–60 s, needs internet); later boots are fast.
> Event photos load from `picsum.photos` — internet needed for posters (gradient fallback otherwise).

---

## 2. Get the code

```bash
git clone https://github.com/Deepnar/EventPass.git
cd EventPass/rapidrail
```

---

## 3. Run it — option A: one command (recommended, Docker)

```bash
./start.sh
```

This script starts (or creates) the `eventpass_mysql` Docker container, waits for MySQL,
then runs Spring Boot. Open **http://localhost:8081**. Stop with `Ctrl+C`
(MySQL keeps running for next time).

What the script does = the manual steps below, so prefer it unless Docker is unavailable.

## 3. Run it — option B: manual with Docker

```bash
# MySQL 8 in Docker (first time only) — use any strong password, it stays on your machine
docker run -d --name eventpass_mysql \
  -e MYSQL_ROOT_PASSWORD="$DB_PASSWORD" \
  -e MYSQL_DATABASE=eventpass \
  -p 3306:3306 mysql:8.0

# App (DB password from rapidrail/.env or environment — never from the repo)
cd rapidrail
mvn spring-boot:run
```

## 3. Run it — option C: local MySQL, no Docker (college Windows laptop)

1. Install MySQL Server + Workbench; remember the `root` password you set.
2. In Workbench, run: `CREATE DATABASE eventpass;` (tables auto-create on boot).
3. Point the app at it — either env var or one-line file edit:
   ```bash
   set DB_PASSWORD=your_mysql_root_password   # cmd, before running
   ```
   or edit `rapidrail/src/main/resources/application.properties`:
   ```properties
   spring.datasource.password=YOUR_MYSQL_WORKBENCH_PASSWORD
   ```
4. `cd rapidrail` → `mvn spring-boot:run` → open http://localhost:8081

To wipe and start fresh (any setup): `DROP DATABASE eventpass; CREATE DATABASE eventpass;`
then restart the app — Hibernate recreates tables and reseeds demo data.

---

## 4. Configuration reference

| Setting | Default | Override |
|---|---|---|
| App port | `8081` | `server.port` in `application.properties` |
| DB host/port/name/user | `localhost:3306` / `eventpass` / `root` | `spring.datasource.*` in `application.properties` |
| DB password | *(none in repo)* — generated into `rapidrail/.env` on first `./start.sh` | `DB_PASSWORD` env var or `.env` file |
| Razorpay key id | built-in TEST key (works out of the box) | `RAZORPAY_KEY_ID` env var |
| Razorpay secret | built-in TEST key | `RAZORPAY_KEY_SECRET` env var |

`./start.sh` also accepts `DB_PASSWORD`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`
from the environment and forwards them. Only **test** Razorpay keys are bundled —
no real money can move; use your own keys from https://dashboard.razorpay.com
for anything beyond demo.

---

## 5. First run — accounts & demo data

Boot seeds **3 organizers** (access code `organizer123` for all) and **12 events**
(4 concerts, 4 workshops, 2 seminars, 2 others, ₹99–₹1099):

| Organizer login (organizer.html) | Org |
|---|---|
| `aarav@rhythmnights.in` / `organizer123` | Rhythm Nights |
| `meera@skillHum.in` / `organizer123` | SkillHum Academy |
| `kabir@cityarts.in` / `organizer123` | CityArts Collective |

Attendees self-register on the home page and get a **₹500 wallet bonus**.

### 5-minute demo flow
1. Organizer panel → sign in → Events → **+ Add Event**.
2. Attendee home → register → browse → open an event → quantity `2` → Confirm → **2 unique QR codes**.
3. Organizer → Check-In Scan → paste ticket-1 token → Check In → **VALID**.
4. Same token again → **ALREADY USED** (409). Ticket-2 → **VALID** independently.
5. History tab shows both check-ins.
6. Book once more → My Bookings → Cancel → seats + refund restored; its QR now reads **CANCELLED**.
7. Same token under another event → **WRONG EVENT**; garbage token → **INVALID**.
8. Add Money → Razorpay test checkout (or instant demo top-up) → wallet credited.

---

## 6. Features

- **Attendee:** register/login (salted password hashes), category pills with counts,
  search, "Recommended for you" strip (your booked categories rank first),
  soonest-first upcoming grid, "Almost full" urgency badges, quantity booking,
  multi-QR confirmation, booking history + cancellation, wallet + top-up history.
- **Organizer (server-enforced login, no hardcoded PIN):** organizer CRUD (own profile),
  event CRUD with poster image URL, bookings per event, issued tickets per event,
  check-in scan simulation (consume) + read-only preview, check-in history.
- **Payments:** Razorpay test-mode top-up (initiate → checkout.js → verify, exactly-once
  credit, replay-safe) with instant demo top-up fallback when keys are absent.
- **Correctness:** pessimistic row locks against overselling/double scan, transactional
  booking/cancel, BigDecimal money, unpredictable QR tokens, append-only entry history,
  GETs never mutate state.

---

## 7. REST API (base `http://localhost:8081/api`)

### Auth — `/api/auth`
| Method | Endpoint | Body |
|---|---|---|
| POST | `/register` | `{username, password, email}` → user with ₹500 bonus |
| POST | `/login` | `{username, password}` → user or 401 |

### Users — `/api/users`
| Method | Endpoint | Notes |
|---|---|---|
| GET | `/` | all users |
| GET | `/{id}` | one user |
| POST | `/{id}/recharge` | `{amount}` add wallet money (demo top-up) |
| PUT | `/{id}` | `{email}` update email |
| DELETE | `/{id}` | blocked while CONFIRMED bookings exist |

### Organizers — `/api/organizers`
| Method | Endpoint | Notes |
|---|---|---|
| POST | `/` | `{name, email, organizationName, accessCode}` → 201 (open registration) |
| GET | `/`, `/{id}` | reads |
| POST | `/{id}/verify` | `{accessCode}` → organizer or 401 (login check) |
| PUT | `/{id}` | needs `X-Organizer-Code` header (own profile only) |
| DELETE | `/{id}` | needs code; blocked while organizer owns events |

### Events — `/api/events` (writes need `X-Organizer-Id` + `X-Organizer-Code`)
| Method | Endpoint | Notes |
|---|---|---|
| GET | `/` | all; filters `?category=` `?organizerId=` `?upcoming=true` `?search=` |
| GET | `/recommended?userId=` | booked categories first, rest soonest-first |
| GET | `/{id}` | one event |
| POST | `/` | create (own organizer) → 201; accepts `imageUrl` poster |
| PUT | `/{id}` | update; capacity can't drop below sold; 403 on others' events |
| DELETE | `/{id}` | blocked while CONFIRMED bookings exist |

### Bookings — `/api/bookings`
| Method | Endpoint | Notes |
|---|---|---|
| POST | `/` | `{userId, eventId, quantity}` → 201 `{booking, tickets, qrCodes}` |
| GET | `/{bookingId}` | booking + tickets + QR images |
| GET | `/user/{userId}` | attendee history |
| GET | `/event/{eventId}` | organizer view of an event's bookings |
| GET | `/` | all bookings |
| POST | `/{bookingId}/cancel` | restores seats + refunds once; blocked after any check-in |
| DELETE | `/{bookingId}` | only CANCELLED bookings (safe cleanup) |

### Tickets — `/api/tickets` (reads + safe cleanup only)
| Method | Endpoint | Notes |
|---|---|---|
| GET | `/{ticketId}`, `/qr/{qrToken}` | ticket + QR image |
| GET | `/booking/{bookingId}`, `/event/{eventId}`, `/` | listings |
| GET | `/validate/{qrToken}` | **read-only** status preview (never consumes) |
| DELETE | `/{ticketId}` | only CANCELLED tickets |

### Check-in — `/api/checkin`
| Method | Endpoint | Notes |
|---|---|---|
| POST | `/` | `{qrToken, eventId, organizerId, organizerCode}` → `200 VALID` · `409 ALREADY_USED` · `422 CANCELLED/WRONG_EVENT` · `404 INVALID` · `401` bad organizer code |
| GET | `/preview?qrToken=&eventId=` | read-only preview, no state change |
| GET | `/event/{eventId}`, `/organizer/{organizerId}`, `/`, `/ticket/{ticketId}` | history reads |

### Payments (Razorpay test mode) — `/api/payments`
| Method | Endpoint | Notes |
|---|---|---|
| GET | `/config` | `{enabled, keyId?}` — UI probe |
| POST | `/initiate` | `{userId, amount}` → `{orderId, ...}` + PENDING row |
| POST | `/verify` | `{orderId, paymentId, signature}` → credits wallet exactly once |
| POST | `/failure` | `{orderId, failureReason}` → marks FAILED |
| GET | `/status/{orderId}`, `/history/{userId}` | status / top-up history |

Restricted by design (not missing): no direct ticket create/update, no check-in
PUT/DELETE (history is append-only), no deletes with active dependents, no booking
delete unless CANCELLED.

---

## 8. Project structure

```
rapidrail/
├── pom.xml                        # Spring Boot 3.4.1, Java 21, ZXing, Razorpay SDK, H2 (tests)
├── start.sh                       # one-command startup: MySQL container + app
└── src/main/java/com/eventpass/eventpass/
    ├── EventPassApplication.java
    ├── DataSeeder.java            # idempotent demo seed (3 orgs, 12 events)
    ├── entity/                    # User, Organizer, Event, Booking, Ticket, CheckIn, Payment
    ├── repository/                # Spring Data JPA + PESSIMISTIC_WRITE locks
    ├── service/                   # BookingService, CheckInService, PaymentService, ...
    ├── controller/                # REST: auth, users, organizers, events, bookings,
    │                              #        tickets, checkin, payments
    └── util/PasswordUtil.java     # salted SHA-256 hashing
└── src/main/resources/static/     # index / dashboard / organizer + vanilla JS + CSS
└── src/test/                      # 24 tests (H2, no MySQL needed)
```

---

## 9. Tests

```bash
cd rapidrail && mvn test
```

24 tests, all passing: booking rules (5), check-in outcomes incl. read-only preview (7),
organizer CRUD/auth/posters (3), Razorpay signature + exactly-once credit (6),
recommendations (3).

---

## 10. Browsing the database (DBeaver)

New Connection → MySQL: Host `localhost`, Port `3306`, Database `eventpass`,
user `root`, password = your `DB_PASSWORD` (see `rapidrail/.env` when using `./start.sh`).
Test Connection → Finish → Databases → `eventpass` → Tables
(`users, organizers, events, bookings, tickets, check_ins, payments`) → View Data.

---

## 11. Troubleshooting

| Symptom | Fix |
|---|---|
| `Port 8081 already in use` | A previous run is still alive — `mvn spring-boot:run` forks **two** JVMs (Maven parent + app). Kill **both**: `for p in $(ps -e -o pid,args \| grep -E "spring-boot:run\|EventPassApplication" \| grep -v grep \| awk '{print $1}'); do kill $p; done`. Then reload the page (stale code otherwise). |
| UI looks like an older version | Same cause as above — old JVM still serving. Kill both processes, restart, hard-refresh (`Ctrl+Shift+R`). |
| No Razorpay button | Backend booted without keys: `GET /api/payments/config` → `{"enabled":false}`. Restart with `RAZORPAY_KEY_ID/SECRET` set. |
| `Communications link failure` / access denied | MySQL not running (`docker start eventpass_mysql`) or wrong `DB_PASSWORD`. |
| `Unknown database 'eventpass'` | `CREATE DATABASE eventpass;` then restart (or let `createDatabaseIfNotExist` handle it via Docker path). |
| Posters don't load | Needs internet (picsum.photos); offline shows gradient placeholders. |
| Slow first boot | Normal — Maven downloads dependencies once into `~/.m2`. |

---

## 12. Notes & limitations

- Quantity-based general admission (no seat map); single-node locking — a college
  demo, not a distributed design.
- Organizer access codes travel as request headers (fine locally, not production auth).
- Only Razorpay **test** keys are bundled; real payments, email/SMS, analytics are
  intentionally out of scope.
