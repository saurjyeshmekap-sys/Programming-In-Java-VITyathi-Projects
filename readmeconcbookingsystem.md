# Concurrent Seat Reservation System

A terminal-based, single-file Java application that simulates a movie/show seat reservation system with thread-safe concurrency control and optional MySQL persistence. It demonstrates one specific problem: given many users trying to book from a shared, limited pool of seats at the same instant, how do you guarantee that exactly one of them wins each seat, with no seat ever handed out twice.

Most naive implementations pass casual testing and demo fine, then fail under real concurrent load in ways that are hard to reproduce, because the failure depends on the exact timing of multiple threads. This project makes that failure mode visible, then fixes it, then lets you toggle the fix on and off so you can watch both states.

---

## Table of Contents

1. [Overview](#overview)
2. [Design Philosophy](#design-philosophy)
3. [Project Structure](#project-structure)
4. [Prerequisites](#prerequisites)
5. [Setup and Running](#setup-and-running)
6. [Database Configuration (Optional)](#database-configuration-optional)
7. [Using the Application](#using-the-application)
8. [How the Concurrency Safety Works](#how-the-concurrency-safety-works)
9. [Java Concurrency Primer](#java-concurrency-primer)
10. [Exception Design](#exception-design)
11. [Class Reference](#class-reference)
12. [Logging and Output Files](#logging-and-output-files)
13. [Performance Notes](#performance-notes)
14. [Security Notes](#security-notes)
15. [Known Limitations](#known-limitations)
16. [Possible Future Improvements](#possible-future-improvements)
17. [Manual Testing Scenarios](#manual-testing-scenarios)
18. [Troubleshooting](#troubleshooting)
19. [FAQ](#faq)
20. [Glossary](#glossary)
21. [Appendix A — Error Message Reference](#appendix-a--error-message-reference)
22. [Appendix B — Sample Log Output](#appendix-b--sample-log-output)
23. [Appendix C — Database Schema and Queries](#appendix-c--database-schema-and-queries)
24. [Appendix D — Suggested Unit and Concurrency Tests](#appendix-d--suggested-unit-and-concurrency-tests)
25. [License](#license)

---

## Overview

This project simulates a single "show" — one screening of one movie at one time. Once running, you can:

- View a color-coded seat map in your terminal.
- Reserve a seat, and cancel a seat you've already reserved.
- Run an automated stress test where ten simulated users all try to grab from a pool of five seats at the exact same time, and watch the system guarantee that no two users ever end up holding the same seat.

Colors render via ANSI escape codes, supported by default in Windows Terminal, the VS Code integrated terminal, Terminal.app, iTerm2, and essentially every Linux terminal. The one common exception is the legacy `cmd.exe` shell on older Windows systems — the program still runs correctly there, you just see raw escape sequences instead of color.

MySQL is optional. Without it, the program keeps state in memory for the session and still writes a plain-text audit trail to `booking_log.txt`. With it, bookings persist across restarts (menu option 5 reads them back).

```bash
git clone <your-repository-url>
cd <repository-folder>
```

If the project isn't in version control, just put `Main.java` in an empty folder and open a terminal there.

## Design Philosophy

**Single file, on purpose.** No `pom.xml`, no module path, nothing beyond a JDK. Copy one file, compile it, run it — appropriate for something meant to be read start to finish or submitted as a single deliverable.

**Fail soft, not hard.** A missing or misconfigured database never crashes the program. Core booking logic has zero hard dependency on MySQL, the same way a production system shouldn't take checkout offline just because an analytics service is unreachable.

**Make the concurrency visible, not just correct.** The booking method includes a deliberate artificial delay and a "run the race" demo mode, so you can watch contention happen, remove the lock to watch it fail, then restore it to watch it get fixed.

**Specific errors over generic ones.** Four purpose-built exception types mean both console output and the log file say exactly what went wrong — not "booking failed," but "this seat doesn't exist," "this seat is already booked by someone else," or "you tried to cancel a seat you didn't book."

## Project Structure

```
.
├── Main.java          # The entire application lives here
├── booking_log.txt    # Created automatically the first time you log an event
└── README.md          # You're reading it
```

`javac` generates one `.class` file per class defined in `Main.java`. Java allows exactly one `public` top-level class per file, sharing the file's name (`public class Main`), but places no such restriction on the non-public classes that live alongside it in the same file.

| Class / Type | Responsibility |
|---|---|
| `SeatStatus` | Two-value enum: `AVAILABLE` or `BOOKED`. |
| `InvalidSeatException` | Thrown when a seat number doesn't exist for the show. |
| `SeatAlreadyBookedException` | Thrown when booking a seat that's already taken. |
| `SeatNotBookedException` | Thrown when cancelling a seat that isn't currently booked. |
| `UnauthorizedCancellationException` | Thrown when cancelling a seat booked by a different user. |
| `Seat` | One physical seat: ID, status, and who (if anyone) booked it. |
| `Booking` | Immutable record of a completed booking — who, which seat, which show, when. |
| `Show` | Owns the full seat collection for one screening; contains all thread-safe booking/cancellation logic. |
| `Ansi` | Constants for colored terminal text. |
| `BookingLogger` | Appends timestamped lines to `booking_log.txt`, safely across threads. |
| `DBManager` | Wraps the JDBC connection and the SQL used to save, remove, and fetch bookings. |
| `UserBookingTask` | `Runnable` representing one simulated user trying to book one seat. |
| `CancelBookingTask` | Cancellation equivalent of `UserBookingTask`. |
| `Main` | Entry point — sets everything up, runs the menu loop, coordinates the demo. |

## Prerequisites

### 1. JDK 11 or newer

The code uses `java.time.LocalDateTime` and `java.util.concurrent` (`ExecutorService`, `Executors`, `TimeUnit`), all stable since well before JDK 11. JDK 17 or 21 (both LTS) are fine defaults for a fresh install.

```bash
java -version
javac -version
```

If either fails, install a JDK. If both succeed but report a version older than 11, upgrade.

| Platform | Command |
|---|---|
| Windows | Install from [Adoptium Temurin](https://adoptium.net/) or Oracle; let the installer add Java to `PATH`. |
| macOS (Homebrew) | `brew install openjdk@17`, then follow the `PATH`-linking instructions it prints. |
| macOS (no Homebrew) | Download a `.pkg` from Adoptium. |
| Ubuntu / Debian | `sudo apt update && sudo apt install openjdk-17-jdk` |
| Fedora / RHEL | `sudo dnf install java-17-openjdk-devel` |
| Windows (Chocolatey) | `choco install temurin17` |

Reopen your terminal afterward so it picks up the updated `PATH`, then re-run `java -version` / `javac -version`.

### 2. A terminal with ANSI color support (optional)

Every terminal in common use today supports this by default. Old-school `cmd.exe` is the one exception, and it's cosmetic only — the program still runs correctly.

### 3. MySQL Server (optional)

Only needed for bookings to persist across runs. See [Database Configuration](#database-configuration-optional).

### 4. MySQL Connector/J (optional)

The JDBC driver `.jar`, needed only alongside MySQL. Download link and wiring instructions are in the database section.

## Setup and Running

```bash
ls              # confirm Main.java is present (dir on Windows Command Prompt)
javac Main.java # compiles to a handful of .class files, one per class
java Main       # note: the class name, not Main.java or Main.class
```

You'll see a startup banner and the main menu. No database is required to get this far.

If you're using MySQL persistence, the JDBC driver must be on the classpath for **both** the compile and run steps:

**macOS / Linux:**
```bash
javac -cp .:mysql-connector-j-8.4.0.jar Main.java
java -cp .:mysql-connector-j-8.4.0.jar Main
```

**Windows (Command Prompt):**
```cmd
javac -cp .;mysql-connector-j-8.4.0.jar Main.java
java -cp .;mysql-connector-j-8.4.0.jar Main
```

The separator is a colon on macOS/Linux and a semicolon on Windows — that's platform classpath syntax, not a typo. Swap in the actual filename of the jar you downloaded if the version differs.

## Database Configuration (Optional)

**You do not need a database to use this project.** If MySQL isn't installed, isn't running, or the credentials are wrong, the program detects this at startup, prints:

```
DB not available, continuing without persistence: ...
```

...and continues normally. Booking and cancelling still work; they're just written only to the in-memory seat map (for that run) and to `booking_log.txt` (always, regardless of the database).

To wire up the database:

**1. Install MySQL Server**

| Platform | Command |
|---|---|
| macOS (Homebrew) | `brew install mysql && brew services start mysql` |
| Ubuntu / Debian | `sudo apt install mysql-server && sudo systemctl start mysql` |
| Windows | [MySQL Installer](https://dev.mysql.com/downloads/installer/) from Oracle. |
| Docker (any platform) | `docker run --name booking-mysql -e MYSQL_ROOT_PASSWORD=your_password -e MYSQL_DATABASE=booking_db -p 3306:3306 -d mysql:8.0` |

The Docker command spins up MySQL 8, sets the root password, and pre-creates `booking_db` in one step — skip step 2 if you used it.

**2. Create the database**

```bash
mysql -u root -p
```
```sql
CREATE DATABASE booking_db;
```

The `bookings` table itself is created automatically on first successful connection via `CREATE TABLE IF NOT EXISTS`, so it's safe to run the app repeatedly.

**3. Download the JDBC driver**

Get `mysql-connector-j` (the "Platform Independent" `.jar`) from the [official downloads page](https://dev.mysql.com/downloads/connector/j/) or Maven Central, and place it next to `Main.java`.

**4. Point the app at your database**

In `Main.java`, inside `DBManager`:

```java
private static final String URL = "jdbc:mysql://localhost:3306/booking_db";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "your_password";
```

Update `DB_USER` and `DB_PASSWORD` to match your setup, and adjust `URL` if your instance runs elsewhere or you named the database differently. Then compile and run with the driver on the classpath, per [Setup and Running](#setup-and-running). Once connected, menu option 5 returns real rows instead of a "DB not connected" message.

## Using the Application

```
1. View seat map
2. Book a seat
3. Cancel a booking
4. Run concurrency demo (10 users, 5 seats)
5. View bookings stored in DB
6. Exit
```

The seat map has 10 seats, five per row, with a "SCREEN THIS WAY" banner above it.

**1 — View seat map.** Prints the grid: available seats green, booked seats red, with a legend and live counts underneath.

**2 — Book a seat.** Shows the seat map, then asks for your name and a seat number. Success prints a confirmation and logs it; failure (seat taken, or seat doesn't exist) prints a specific error rather than a generic one.

**3 — Cancel a booking.** Same flow, but you can only cancel a seat *you* booked — a different name than the original booker gets rejected with an "unauthorized" error, simulating a lightweight ownership check.

**4 — Run concurrency demo.** Creates ten simulated users (`DemoUser1`–`DemoUser10`) and, via a thread pool, has all of them attempt bookings at essentially the same moment, competing for five contested seats — fewer seats than users, guaranteeing contention. You'll see a burst of `SUCCESS`/`FAILED` lines as threads race, followed by the final seat map. No matter how many times you run it, no seat is ever booked by two different demo users.

**5 — View bookings stored in DB.** If MySQL is connected, lists every row from `bookings`, ordered by booking time. If not connected, explains that nothing can be shown from the database (in-memory bookings for the current session still work fine).

**6 — Exit.** Closes the database connection if one was open, prints a goodbye message pointing to `booking_log.txt`, and ends the program.

## How the Concurrency Safety Works

The underlying problem is a textbook race condition. Imagine two threads both trying to book seat 3 at nearly the same moment. In a naive (unsynchronized) implementation: check if the seat is available, then mark it booked. If Thread A checks seat 3 and sees it's available, but hasn't yet marked it booked when Thread B also checks — seat 3 still looks available to Thread B too. Both threads now believe they're clear, both write "booked," and one overwrites the other's claim. One seat, two "successful" bookings, corrupted state.

This project prevents that by wrapping the entire check-then-book sequence inside a `synchronized` method on `Show`. Only one thread can execute that method (on that object) at any given time; every other thread trying to enter blocks until it's done. The check-then-book sequence becomes effectively atomic from any other thread's perspective.

To make this actually observable, `bookSeat()` includes a deliberate `Thread.sleep(50)` between the availability check and the write — 50ms is tiny in absolute terms, but enough for a pool of competing threads to visibly pile up on the lock.

To see the failure mode yourself: remove `synchronized` from `bookSeat`'s signature, recompile, and re-run the demo a few times. Without the lock, that same artificial delay now creates a real window for two threads to both see a seat as available and both write to it, and you should see duplicate bookings on the same seat. Put `synchronized` back, and the problem disappears. That before-and-after comparison is the core point of this project.

### Sequence: two threads racing for the same seat

```
Thread A                    Show (lock)                  Thread B
   |--- bookSeat(3, "Alice") -->|                            |
   |   (acquires lock)          |<-- bookSeat(3, "Bob") -----|
   |                            |   (blocks, waiting for lock)
   |   seat.getStatus() == AVAILABLE                         |
   |   Thread.sleep(50)  [Thread B stays blocked the whole time]
   |   seat.markBooked("Alice") |                            |
   |   (releases lock)          |                            |
   |<-- returns normally -------|   (acquires lock, finally) |
                                |   seat.getStatus() == BOOKED
                                |   throw SeatAlreadyBookedException
                                |   (releases lock)           |
                                |----- exception thrown ----->|
```

Thread B isn't merely unlucky — it's mechanically blocked from even entering `bookSeat()` for as long as Thread A holds the lock, including through the sleep. By the time Thread B proceeds, Thread A's write has already completed and is visible (via the lock hand-off and `Seat`'s `volatile` fields), so Thread B correctly sees `BOOKED` and fails instead of racing.

## Java Concurrency Primer

**Threads.** An independent path of execution within a program; several can run "at the same time," either truly in parallel across cores or interleaved on one. Each simulated user in the demo runs on its own thread.

**Race conditions.** A bug where correctness depends on the relative timing of multiple threads accessing shared, mutable state without coordination. The double-booking scenario above is a textbook example.

**`synchronized`.** Java's built-in mutual-exclusion mechanism. Marking a method `synchronized` means Java acquires the calling object's monitor lock before running the method body and releases it on return (normal or exceptional); any other thread calling a `synchronized` method on that same object waits.

**`volatile`.** Solves a different problem than `synchronized`: visibility, not exclusion. It guarantees a write by one thread is seen by subsequent reads from other threads, rather than a stale cached copy. `Seat`'s `status` and `bookedBy` fields are `volatile` so any thread reading seat state sees the latest value, even outside `Show`'s locked methods. `volatile` alone would *not* prevent the double-booking race — it only fixes visibility, not mutual exclusion. This project needs both, each solving a different half of the problem.

**Thread pools / `ExecutorService`.** Spinning up a new OS thread per task is relatively expensive; a pool reuses a fixed set of worker threads instead. `Executors.newFixedThreadPool(n)` creates a pool of `n` workers. The demo uses a pool of ten, one per simulated user.

**`Runnable`.** An interface for "a task with no return value," implemented via `run()`. `UserBookingTask` and `CancelBookingTask` both implement it, bundling everything needed for one attempt — show, seat, user, logger, and optional database manager.

**Checked exceptions.** The compiler forces callers to catch or declare them, unlike unchecked exceptions. All four of this project's custom exceptions extend `Exception` directly, making them checked, so every caller of `bookSeat()` or `cancelBooking()` must explicitly handle these failure modes.

## Exception Design

Four narrowly-scoped types, each corresponding to exactly one way a booking or cancellation attempt can fail:

- **`InvalidSeatException`** — the seat number doesn't correspond to any seat for the show.
- **`SeatAlreadyBookedException`** — the seat's status is already `BOOKED`. The message names who currently holds it.
- **`SeatNotBookedException`** — the seat is currently `AVAILABLE`; there's nothing to cancel.
- **`UnauthorizedCancellationException`** — the name supplied for cancellation doesn't match the name that booked the seat. Enforced inside the same `synchronized` block as the rest of `cancelBooking()`, so there's no window for a race between checking ownership and cancelling.

Each is a minimal subclass of `Exception` carrying just a human-readable message. Every throw site is caught close to the attempt (in `UserBookingTask.run()` / `CancelBookingTask.run()`), where the failure is printed to the console and written to the log with the exact exception message.

## Class Reference

**`Seat`** — `seatId` (final), `status` and `bookedBy` (both `volatile`). Constructor initializes every seat to `AVAILABLE` with no booker. `markBooked(userName)` and `markAvailable()` are only ever called from `Show`'s already-`synchronized` methods, so mutual exclusion comes from `Show`; `volatile` here is purely about visibility for readers.

**`Booking`** — Four `final` fields (`seatId`, `userName`, `showName`, `bookedAt`, captured via `LocalDateTime.now()`), getters only, no mutation after construction — a booking is a historical fact.

**`Show`** — The core class. Holds a `LinkedHashMap<Integer, Seat>` (preserves insertion order so seats print 1 through N):
- **`bookSeat(int seatId, String userName)`** — `synchronized`. Validates existence (`InvalidSeatException`), validates availability (`SeatAlreadyBookedException`), sleeps 50ms, then `seat.markBooked(userName)`.
- **`cancelBooking(int seatId, String userName)`** — `synchronized`. Validates existence, validates it's currently booked (`SeatNotBookedException`), validates ownership (`UnauthorizedCancellationException`), then `seat.markAvailable()`.
- **`getAnyBookedSeatId()`, `getBookedByForSeat(int)`, `getTotalSeats()`** — small `synchronized` queries.
- **`printSeatMap()`** — plain-text dump. **`printSeatGrid()`** — the colored cinema-style grid used by the menu.

**`Ansi`** — Six `static final String` constants (`RESET`, `BOLD`, `GREEN`, `RED`, `CYAN`, `YELLOW`) holding raw ANSI escape sequences, plus a private constructor so the class is never instantiated.

**`BookingLogger`** — One `synchronized log(String message)` method, opening the file in append mode via `new FileWriter(logFilePath, true)` inside try-with-resources, writing a timestamp-prefixed line.

**`DBManager`** — A thin wrapper around `java.sql`, no ORM:
- **`connect()`** — opens the connection, ensures the `bookings` table exists.
- **`createTableIfNotExists()`** — `CREATE TABLE IF NOT EXISTS`, safe to call on every startup.
- **`saveBooking(Booking)`** — inserts via `PreparedStatement` with bound parameters; called by `UserBookingTask` after a successful booking, only if a `DBManager` was supplied.
- **`removeBooking(String showName, int seatId)`** — deletes the matching row on a successful cancellation.
- **`fetchAllBookings()`** — `SELECT ... ORDER BY booked_at`, returned as formatted strings; powers menu option 5.

Every DB-touching method catches `SQLException` locally and prints to `System.err` rather than propagating — consistent with "fail soft."

**`UserBookingTask` / `CancelBookingTask`** — Both `Runnable`. Constructors take the `Show`, target `seatId`, acting `userName`, shared `BookingLogger`, and an optional `DBManager`. `run()` attempts the operation, catches the relevant checked exceptions, prints and logs the result, and updates the database if one was supplied and the operation succeeded.

**`Main`** — `main()` creates the single `Show`, `BookingLogger`, and attempts a `DBManager` connection (catching `SQLException` and proceeding without it on failure), then runs the menu loop. `runConcurrentDemo()` builds a ten-worker pool, generates ten `UserBookingTask`s (each targeting a random seat among the five contested ones), submits them, waits via `awaitTermination`, then prints the final grid.

## Logging and Output Files

- **`booking_log.txt`** — created automatically in the launch directory on the first logged event. Every line is timestamped and describes exactly one event: successful/failed booking, successful/failed cancellation, with the specific reason on failure.

`log()` is `synchronized` because two threads writing to the same file at nearly the same time, uncoordinated, can interleave their writes at the byte level and corrupt the file. Locking it means each call finishes its entire line before the next call is allowed to begin; combined with append mode, this guarantees a clean, chronologically ordered record even under the demo's heavy concurrent load.

## Performance Notes

`Show`'s `synchronized` methods fully serialize all booking/cancellation operations for that show — only one can be in progress at any instant, system-wide. For ten threads and a handful of seats this is invisible in practice. At much larger scale (thousands of concurrent bookings per second), this coarse-grained, whole-object locking would become a bottleneck, since operations targeting entirely unrelated seats still queue behind the same lock. A more scalable design would use per-seat locking, or a `ConcurrentHashMap` with atomic compare-and-swap on individual seat state, so booking seat 1 and booking seat 7 could proceed in parallel. That's intentionally out of scope here — the whole-show lock keeps the logic easy to read and easy to verify as correct, which was the priority.

The artificial 50ms delay in `bookSeat()` exists purely to make contention visible during the demo. In a real deployment, remove it — it does nothing but slow things down once you're not deliberately demonstrating a race condition.

## Security Notes

Honest caveats — this is a demo project, not production-ready as-is:

- **Database credentials are hardcoded** as constants in the source. Fine for a local demo; in a real deployment these belong in environment variables, a secrets manager, or a `.gitignore`'d config file, never committed.
- **There's no authentication.** "Authorization" is a plain-text name match between booker and canceller — no password, no session, no identity verification. Anyone can type any name; this isn't a security boundary.
- **SQL injection is guarded against** — every query with user-provided data uses a `PreparedStatement` with bound parameters, never string concatenation.
- **The name field accepts arbitrary text** with no validation, sanitization, or length limit — fine for a terminal demo, would need tightening for any networked, untrusted-input context.

## Known Limitations

- Only one `Show` exists per running instance — no support for multiple simultaneous shows.
- No way to view historical/cancelled bookings after the program exits beyond `booking_log.txt`, and that log isn't designed to be re-imported.
- Seat layout is fixed at ten seats, five per row, defined in `main()` — changing it means editing and recompiling.
- No network layer — single-process, single-machine, not client-server.
- Error messages are for a human reading the terminal, not structured for machine parsing.

## Possible Future Improvements

- Split into a proper multi-file, multi-package project with Maven/Gradle.
- Support multiple simultaneous shows, each with an independent seat map.
- Replace the whole-show lock with per-seat locking or a lock-free, atomic approach.
- Wrap the booking logic behind a REST API.
- Move database credentials into environment variables or a properties file.
- Add automated unit and concurrency tests (see [Appendix D](#appendix-d--suggested-unit-and-concurrency-tests)).
- Add real authentication.

## Manual Testing Scenarios

1. **Happy path.** Book seat 1 as "Alice." Seat 1 shows red, booked by Alice.
2. **Double booking.** With seat 1 booked by Alice, book seat 1 as "Bob" → `SeatAlreadyBookedException` naming Alice.
3. **Invalid seat.** Book seat 99 (10-seat show) → `InvalidSeatException`.
4. **Cancel never-booked.** Cancel seat 5 without booking it → `SeatNotBookedException`.
5. **Unauthorized cancel.** With seat 1 booked by Alice, cancel as "Bob" → `UnauthorizedCancellationException`; seat 1 stays booked by Alice.
6. **Proper cancel.** Cancel seat 1 as "Alice" → succeeds, seat 1 shows available again.
7. **Concurrency demo, repeated.** Run option 4 several times; each contested seat ends up booked by exactly one demo user, or stays available.
8. **Database round-trip (if configured).** Book a seat, confirm via option 5, cancel it, confirm the row is gone.

## Troubleshooting

| Problem | Likely Cause and Fix |
|---|---|
| `javac: command not found` | JDK isn't installed or isn't on `PATH`. Install a JDK, open a new terminal. |
| Colors show as `[36m` instead of actual color | Terminal doesn't interpret ANSI codes. Cosmetic only — try Windows Terminal, VS Code's terminal, or iTerm2 for real colors. |
| `DB not available, continuing without persistence: ...` | MySQL isn't running/reachable, or credentials in `DBManager` are wrong. Informational, not fatal — safe to ignore if you don't need persistence. |
| `ClassNotFoundException` or driver errors with MySQL | JDBC driver `.jar` isn't on the classpath. Check the `-cp` flags for both `javac` and `java`; colon on macOS/Linux, semicolon on Windows. |
| Program exits immediately after `java Main` | Confirm you're in the directory with the `.class` files, and you ran `java Main`, not `java Main.java`. |
| `Access denied for user 'root'@'localhost'` | Wrong username/password in `DBManager`, or that user lacks access to `booking_db`. Verify with `mysql -u root -p` directly. |
| Seat grid looks misaligned or wrapped | Terminal window is too narrow. Widen it. |
| Nothing written to `booking_log.txt` | Check write permission on the launch directory. |
| Concurrency demo seems to hang briefly | Expected — it waits up to 10 seconds (`pool.awaitTermination(10, TimeUnit.SECONDS)`) before printing results, though it usually finishes much faster. |
| `UnsupportedClassVersionError` | `.class` files were compiled with a newer JDK than the one running `java`. Recompile with the same JDK, or update the runtime. |

## FAQ

**Do I need MySQL at all?** No — it's entirely optional; you just lose persistence across separate runs.

**Why plain JDBC instead of Hibernate or Spring Data?** To keep everything self-contained in one file with zero framework dependencies beyond the optional driver, and to keep the actual SQL transparent.

**Can I change the number of seats?** Yes, by editing `int totalSeats = 10;` in `Main.main()`, then recompiling.

**Can I change the demo's user/seat counts?** Yes — `numUsers` and `contestedSeats` inside `runConcurrentDemo()`.

**Why the artificial `Thread.sleep(50)`?** To make the race condition (and its fix) reliably observable during a short demo run. Without it, the conflict window is often too small to trigger visibly, even unsynchronized. Remove it for a real deployment.

**Is this safe as a real booking backend?** Not as-is — see [Security Notes](#security-notes) and [Known Limitations](#known-limitations).

**What happens if the DB connection drops mid-session?** Each database operation catches its own `SQLException` independently; in-memory state and file logging keep working regardless.

**Why is only `Main` declared `public`?** Java allows one `public` top-level class per file, matching the filename. Everything else stays package-private, which is fine since nothing outside the file references them.

## Glossary

- **Race condition** — A bug where correctness depends on the unpredictable relative timing of multiple threads.
- **Mutual exclusion** — Guaranteeing only one thread at a time executes a given piece of code or accesses a given resource.
- **`synchronized`** — Java's keyword for mutual exclusion via an object's intrinsic monitor lock.
- **`volatile`** — Guarantees writes to a field by one thread are visible to other threads' subsequent reads.
- **Thread pool** — A reusable collection of worker threads that tasks can be submitted to.
- **`ExecutorService`** — Java's interface for a thread pool and related task-execution machinery.
- **`Runnable`** — A functional interface for a unit of work with no return value, defined by `run()`.
- **Checked exception** — An exception the compiler forces calling code to catch or declare.
- **JDBC** — Java Database Connectivity; the standard API for connecting to relational databases.
- **`PreparedStatement`** — A JDBC construct for parameterized queries, avoiding SQL injection.
- **ANSI escape code** — A character sequence, interpreted by compatible terminals, that controls text formatting rather than displaying literally.
- **Graceful degradation** — Designing a system so a non-essential component's failure reduces functionality without a full crash.
- **Happens-before relationship** — The Java Memory Model's formal guarantee for when one thread's writes are visible to another thread's reads; established here both by lock release/acquisition and by `volatile` field writes/reads.
- **Monitor** — The intrinsic lock every Java object has, acquired and released automatically by `synchronized`.
- **`LinkedHashMap`** — A `Map` that preserves insertion order; used by `Show` so seats iterate in numeric order.

## Appendix A — Error Message Reference

| Exception | When it's thrown | Example message |
|---|---|---|
| `InvalidSeatException` | Seat number doesn't exist for the show | "Seat 99 does not exist for show 'Avengers: Doomsday - 7:00 PM'." |
| `SeatAlreadyBookedException` | Booking a seat that's already taken | "Seat 3 is already booked by Alice." |
| `SeatNotBookedException` | Cancelling a seat that isn't booked | "Seat 5 is not currently booked, nothing to cancel." |
| `UnauthorizedCancellationException` | Cancelling a seat booked by someone else | "Bob cannot cancel seat 1 because it was booked by Alice." |

## Appendix B — Sample Log Output

```
[2026-09-15T01:12:04.512] SUCCESS - Alice booked seat 1 for Avengers: Doomsday - 7:00 PM
[2026-09-15T01:12:37.881] FAILED - Bob tried seat 1 -> Seat 1 is already booked by Alice.
[2026-09-15T01:13:02.190] CANCEL FAILED - Bob tried to cancel seat 1 -> Bob cannot cancel seat 1 because it was booked by Alice.
[2026-09-15T01:13:20.004] CANCELLED - Alice cancelled seat 1 for Avengers: Doomsday - 7:00 PM
[2026-09-15T01:14:01.337] SUCCESS - DemoUser3 booked seat 2 for Avengers: Doomsday - 7:00 PM
[2026-09-15T01:14:01.339] FAILED - DemoUser7 tried seat 2 -> Seat 2 is already booked by DemoUser3.
[2026-09-15T01:14:01.341] SUCCESS - DemoUser1 booked seat 4 for Avengers: Doomsday - 7:00 PM
```

## Appendix C — Database Schema and Queries

```sql
CREATE TABLE IF NOT EXISTS bookings (
    id INT AUTO_INCREMENT PRIMARY KEY,
    show_name VARCHAR(150),
    seat_id INT,
    user_name VARCHAR(100),
    booked_at DATETIME
);
```

```sql
-- See everything, most recent first
SELECT * FROM bookings ORDER BY booked_at DESC;

-- Count bookings per show
SELECT show_name, COUNT(*) AS total_bookings FROM bookings GROUP BY show_name;

-- Check whether a specific seat is currently recorded as booked
SELECT * FROM bookings WHERE show_name = 'Avengers: Doomsday - 7:00 PM' AND seat_id = 3;
```

## Appendix D — Suggested Unit and Concurrency Tests

Not included in the current single-file project — provided as a reference if you add JUnit 5.

```java
@Test
void bookingAvailableSeatSucceeds() throws Exception {
    Show show = new Show("Test Show", 5);
    show.bookSeat(1, "Alice");
    assertEquals("Alice", show.getBookedByForSeat(1));
}

@Test
void bookingAlreadyBookedSeatThrows() throws Exception {
    Show show = new Show("Test Show", 5);
    show.bookSeat(1, "Alice");
    assertThrows(SeatAlreadyBookedException.class, () -> show.bookSeat(1, "Bob"));
}

@Test
void bookingInvalidSeatThrows() {
    Show show = new Show("Test Show", 5);
    assertThrows(InvalidSeatException.class, () -> show.bookSeat(99, "Alice"));
}

@Test
void cancellingUnbookedSeatThrows() {
    Show show = new Show("Test Show", 5);
    assertThrows(SeatNotBookedException.class, () -> show.cancelBooking(1, "Alice"));
}

@Test
void cancellingSomeoneElsesBookingThrows() throws Exception {
    Show show = new Show("Test Show", 5);
    show.bookSeat(1, "Alice");
    assertThrows(UnauthorizedCancellationException.class,
            () -> show.cancelBooking(1, "Bob"));
}
```

A genuine concurrency stress test — many threads targeting the same seat, asserting exactly one wins:

```java
@Test
void onlyOneThreadCanBookTheSameSeatUnderContention() throws Exception {
    Show show = new Show("Test Show", 1);
    int numThreads = 50;
    ExecutorService pool = Executors.newFixedThreadPool(numThreads);
    AtomicInteger successCount = new AtomicInteger(0);
    CountDownLatch startGate = new CountDownLatch(1);

    for (int i = 0; i < numThreads; i++) {
        final String user = "User" + i;
        pool.submit(() -> {
            try {
                startGate.await();
                show.bookSeat(1, user);
                successCount.incrementAndGet();
            } catch (SeatAlreadyBookedException | InvalidSeatException expected) {
                // expected for every loser of the race
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    startGate.countDown(); // release all threads at (almost) the same instant
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, successCount.get(), "Exactly one thread should have won the booking race");
}
```

`CountDownLatch` gets all fifty threads created and waiting at the same starting line, then releases them at essentially the same instant, maximizing contention on the shared seat. Running this with `synchronized` removed from `bookSeat()` should reliably fail (reporting more than one success) — a useful, automatable proof that the locking is doing real work.

## License

Unless a separate `LICENSE` file states otherwise, treat this project as provided for educational and demonstration purposes. Check with the repository owner before reusing or redistributing the concurrency patterns or overall structure.
