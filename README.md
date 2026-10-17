# 🎟️ EventPass — Local Event Ticket Booking & QR Entry Validation

A Spring Boot ticket booking system for local events (concerts, workshops, seminars) with
quantity selection, wallet payment, per-ticket QR codes, and organizer check-in scan simulation.

Migrated from a movie-ticket-booking codebase; same stack, movie domain replaced by events.

---

## Tech Stack

| Layer | Tech |
|---|---|
| Backend | Spring Boot 3.4.1, Java 21 |
| ORM | Spring Data JPA + Hibernate (classes auto-create tables) |
| Database | MySQL 8 |
| QR Codes | ZXing (one QR per ticket, Base64 PNG via API) |
| Frontend | Plain HTML + CSS + vanilla JS (served from Spring Boot `static/`) |
| Build | Maven |
| Tests | JUnit 5 + H2 (test scope only; runtime stays MySQL) |

No React, microservices, Docker requirement, payment gateways, or message queues — intentionally small.

---

## Architecture

```
Browser (static HTML/JS)
    │  fetch() JSON
    ▼
REST Controllers (/api/...)
    ▼
Services (@Transactional business rules)
    ▼
Repositories (JPA)  →  MySQL 8
```

## Entities & Relationships

```
Organizer 1───* Event 1───* Booking *───* Ticket 1───o CheckIn
                  │           │               (one successful
                  │           *───1 User       check-in per ticket)
                  └───* Ticket (denormalized event link for WRONG_EVENT checks)
```

| Entity | Key fields |
|---|---|
| `Organizer` | id, name, email (unique), organizationName, accessCodeHash (salted SHA-256, never serialized) |
| `Event` | id, title, description, category (Concert/Workshop/Seminar/Other), venue, eventDateTime, ticketPrice (BigDecimal 10,2), totalCapacity, availableSeats, organizer (ManyToOne), `@Version` optimistic lock |
| `User` | id, username (unique), password (salted hash), email, balance (BigDecimal 10,2) |
| `Booking` | bookingId (UUID string PK), user (ManyToOne), event (ManyToOne), quantity, totalAmount (BigDecimal), bookingTime, status (CONFIRMED/CANCELLED) |
| `Ticket` | ticketId (UUID PK), booking (ManyToOne), event (ManyToOne), qrToken (unique random UUID — the QR payload), status (VALID/USED/CANCELLED) |
| `CheckIn` | id, ticket (OneToOne, unique — one entry per ticket), event, organizer, checkedInAt |

Money uses `BigDecimal` (paise-exact). QR tokens are random UUIDs, unrelated to sequential IDs.

---

## Setup

### Prerequisites
Java 21, Maven 3.9+, MySQL 8.

### 1. Create the database
```sql
CREATE DATABASE eventpass;
```
Tables are auto-created by Hibernate on first boot. To start fresh later:
```sql
DROP DATABASE eventpass;
CREATE DATABASE eventpass;
```

### 2. Configure MySQL credentials
Either set the env var (recommended, no file edit):
```bash
export DB_PASSWORD='your-mysql-root-password'
```
or edit one line in `rapidrail/src/main/resources/application.properties`:
```properties
spring.datasource.password=YOUR_MYSQL_PASSWORD_HERE
```
Defaults: host `localhost:3306`, database `eventpass`, user `root`, port `8081`.

Docker MySQL (this machine's setup):
```bash
docker run -d --name eventpass_mysql \
  -e MYSQL_ROOT_PASSWORD='***REMOVED_DB_PASSWORD***' \
  -e MYSQL_DATABASE=eventpass \
  -p 3306:3306 mysql:8.0
```

### 3. Run
```bash
cd rapidrail
mvn spring-boot:run
# open http://localhost:8081
```
First boot downloads dependencies (30–60 s) and seeds 2 demo organizers + 5 events.
Stop with `Ctrl+C`.

Seeded organizers (both use access code `organizer123`):
`aarav@rhythmnights.in` (Rhythm Nights) · `meera@skillHum.in` (SkillHum Academy)

---

## REST API (base `http://localhost:8081/api`)

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
| POST | `/{id}/recharge` | `{amount}` add wallet money |
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
| GET | `/{id}` | one event |
| POST | `/` | create (own organizer) → 201 |
| PUT | `/{id}` | update; capacity can't drop below sold; 403 if another organizer's event |
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
| POST | `/` | `{qrToken, eventId, organizerId, organizerCode}` → consumes ticket once. `200 VALID` · `409 ALREADY_USED` · `422 CANCELLED/WRONG_EVENT` · `404 INVALID` · `401` bad organizer code |
| GET | `/preview?qrToken=&eventId=` | read-only preview, no state change |
| GET | `/event/{eventId}`, `/organizer/{organizerId}`, `/`, `/ticket/{ticketId}` | history reads |

### Intentionally restricted operations (business rules, not missing features)
- No direct ticket create/update: tickets come only from booking; state changes only via check-in or booking cancel.
- No check-in PUT/DELETE: entry history is append-only.
- No event/organizer delete with dependents: prevents orphaned bookings/tickets.
- No booking delete unless CANCELLED: inventory/refund rules must always run.
- GETs never mutate: validation preview and ticket reads leave status untouched.

---

## Demo walkthrough (viva-ready, ~5 min)

1. `mvn spring-boot:run`, open `http://localhost:8081`.
2. **Organizer panel** (`organizer.html`): sign in as `aarav@rhythmnights.in` / `organizer123`. Events tab → + Add Event (concert, price, capacity).
3. **Attendee** (`index.html`): register → ₹500 bonus. Browse events → click event → quantity `2` → Confirm. Confirmation shows **2 unique QR codes**.
4. **Check-In tab**: select the event, paste ticket-1 token → **Check In** → `✅ VALID — Entry permitted`.
5. Paste ticket-1 again → `⚠️ ALREADY USED — Entry denied` (409).
6. Paste ticket-2 → `✅ VALID` (independent). History tab shows both records.
7. Book 1 more ticket, then **My Bookings → Cancel**: seats and wallet refund restored exactly; its QR now reports `⛔ CANCELLED`.
8. Paste a ticket-1 token under a *different* event → `🚫 WRONG EVENT`. Paste garbage → `❌ INVALID`.
9. Show organizer/event CRUD, attendee booking history, wallet recharge.

---

## Concurrency & correctness notes

- Booking takes a `PESSIMISTIC_WRITE` row lock on the event (`@Version` as second defence): no negative inventory, no overselling.
- Check-in takes a `PESSIMISTIC_WRITE` lock on the ticket + unique constraint on `check_ins.ticket_id`: the same QR cannot be consumed twice, even concurrently.
- Booking/cancel are `@Transactional`: balance + seats + booking + tickets change atomically.
- Cancel refunds exactly once (second cancel rejected); checked-in tickets block cancellation; cancelled tickets fail validation.

## Security (minimal, demo-appropriate)

- Passwords and organizer access codes are salted SHA-256 hashes — no plaintext secrets, no hard-coded admin PIN.
- Organizer writes are authorized **server-side** (`X-Organizer-Id` + `X-Organizer-Code` headers, events scoped to the owning organizer). The old client-side `1234` PIN gate is gone.

## Tests

```bash
cd rapidrail && mvn test   # 14 tests, H2 in-memory, MySQL not required
```
- `BookingServiceTest` (5): valid booking + per-admission tickets, oversell rejected, insufficient balance rejected, past-event rejected, cancel restores seats/refund exactly once.
- `CheckInServiceTest` (7): valid check-in + history, duplicate → ALREADY_USED, invalid token, wrong event, cancelled ticket, cancel-after-check-in blocked, preview is read-only.
- `CrudAndAuthTest` (2): organizer CRUD with code auth, hashing, duplicate-email rejection.

## Limitations

- Quantity-based general admission only (no seat map).
- Single-node demo locking (pessimistic row locks); not a distributed design.
- No real payments, email/SMS, roles framework, or analytics — out of scope by design.
- Organizer access code travels as a header per request (fine for local demo, not a production auth scheme).
