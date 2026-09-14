# Concurrent Seat Booking System

A single-file Java demonstration of a thread-safe movie-seat booking system. Multiple users compete for a limited number of seats concurrently. The program uses Java synchronization to prevent double-booking, writes every attempt to a log file, and optionally persists successful bookings to a MySQL database.

Everything lives in one source file (`Main.java`) so the project is easy to compile and submit.

---

## Features

- **Thread-safe seat booking** – `synchronized` methods on the `Show` class prevent two threads from booking the same seat.
- **Cancellation support** – only the original booker can cancel; other attempts raise clear exceptions.
- **File logging** – every success / failure / cancellation is appended to `booking_log.txt`.
- **Optional MySQL persistence** – successful bookings can be stored in a database; the program still runs correctly if the database is unavailable.
- **Custom exceptions** – `InvalidSeatException`, `SeatAlreadyBookedException`, `SeatNotBookedException`, `UnauthorizedCancellationException`.

---

## Prerequisites

| Requirement | Version / Notes |
|-------------|-----------------|
| Java JDK    | 8 or later (tested with Java 11+) |
| MySQL (optional) | 5.7 / 8.x if you want database persistence |
| MySQL Connector/J (optional) | Only needed when using the database |

You do **not** need Maven, Gradle, or any other build tool. The project is a single `.java` file.

---

## Project Structure

```
.
├── Main.java          # Complete source (all classes in one file)
└── README.md          # This file
```

After running the program you will also see:

```
booking_log.txt        # Generated log of all booking / cancellation attempts
```

---

## 1. Environment Setup

### Install Java

- **Windows / macOS / Linux**  
  Download a JDK from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/) and install it.

- Verify the installation:

  ```bash
  java -version
  javac -version
  ```

  Both commands should report a version ≥ 1.8.

### (Optional) Install MySQL

If you want database persistence:

1. Install MySQL Server.
2. Start the MySQL service.
3. Create the database:

   ```sql
   CREATE DATABASE booking_db;
   ```

4. (Optional) Create a dedicated user, or use the default `root` account.

---

## 2. Dependency Installation

### Core program (no extra jars required)

The booking logic, concurrency, file logging and console output use only the Java standard library. No additional jars are needed to compile and run the in-memory + log-file version.

### Database support (optional)

1. Download the MySQL Connector/J JAR from the official site:  
   https://dev.mysql.com/downloads/connector/j/

2. Place the JAR (e.g. `mysql-connector-j-8.x.x.jar`) in the same folder as `Main.java`, or note its full path.

---

## 3. Configuration

Open `Main.java` and locate the `DBManager` class. Update the three constants to match your environment:

```java
private static final String URL = "jdbc:mysql://localhost:3306/booking_db";
private static final String DB_USER = "root";
private static final String DB_PASSWORD = "your_password";
```

| Setting       | Description                                      | Example                          |
|---------------|--------------------------------------------------|----------------------------------|
| `URL`         | JDBC connection string                           | `jdbc:mysql://localhost:3306/booking_db` |
| `DB_USER`     | MySQL username                                   | `root`                           |
| `DB_PASSWORD` | MySQL password                                   | `your_password`                  |

If MySQL is not running or the credentials are wrong, the program detects the failure, prints a short message, and continues **without** database persistence. No crash occurs.

---

## 4. Compilation & Execution

### Option A – Without database (recommended first run)

```bash
# Compile
javac Main.java

# Run
java Main
```

### Option B – With MySQL persistence

```bash
# Compile (include the connector on the classpath)
javac -cp .:mysql-connector-j-8.x.x.jar Main.java

# Run (same classpath)
java -cp .:mysql-connector-j-8.x.x.jar Main
```

> **Windows note:** Replace the colon (`:`) with a semicolon (`;`) in the classpath:
> ```bash
> javac -cp .;mysql-connector-j-8.x.x.jar Main.java
> java -cp .;mysql-connector-j-8.x.x.jar Main
> ```

---

## 5. What the Program Does

1. Creates a show with **5 seats**.
2. Launches **10 concurrent users**, each trying to book a random seat.
3. Because there are more users than seats, some booking attempts fail with `SeatAlreadyBookedException` – this is expected and demonstrates that the synchronization works.
4. Prints the final seat map.
5. Runs a short cancellation demo:
   - Owner cancels successfully.
   - A second cancel on the same seat fails (`SeatNotBookedException`).
   - An unauthorized user tries to cancel someone else’s seat (`UnauthorizedCancellationException`).
   - An invalid seat number is attempted (`InvalidSeatException`).
6. If the database is available, prints the rows currently stored in the `bookings` table.
7. Writes a complete audit trail to `booking_log.txt`.

---

## 6. Expected Console Output (example)

```
=== Booking Phase: 10 users competing for 5 seats ===
User3 -> SUCCESS: booked seat 2
User7 -> SUCCESS: booked seat 5
User1 -> FAILED for seat 2: Seat 2 is already booked by User3.
...
--- Seat map for Avengers: Doomsday - 7:00 PM ---
Seat 1 -> BOOKED (booked by User4)
Seat 2 -> BOOKED (booked by User3)
...
=== Cancellation Phase ===
User4 -> CANCELLED seat 1
RandomUser -> CANCEL FAILED for seat 1: Seat 1 is not currently booked, nothing to cancel.
SomeoneElse -> CANCEL FAILED for seat 2: SomeoneElse cannot cancel seat 2 because it was booked by User3.
User1 -> CANCEL FAILED for seat 999: Seat 999 does not exist for show 'Avengers: Doomsday - 7:00 PM'.
...
Full success/failure/cancel log written to booking_log.txt
```

(Exact seat assignments and success/failure messages will vary because seats are chosen randomly.)

---

## 7. Verifying Correctness

- After the booking phase, at most **5** seats should be marked `BOOKED`.
- No seat should ever appear as booked by two different users.
- The log file `booking_log.txt` contains one line for every attempt.
- Removing the `synchronized` keyword from `bookSeat` (and recompiling) will typically produce double-bookings, confirming that the lock is what prevents the race condition.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `javac: command not found` | Install a JDK and ensure it is on your PATH. |
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | Add the MySQL Connector/J JAR to the classpath (see Option B above). |
| `DB not available, continuing without persistence` | MySQL is not running or credentials are wrong. The program still works; fix the connection details if you need persistence. |
| `Access denied for user ...` | Check `DB_USER` / `DB_PASSWORD` and that the user has rights on `booking_db`. |
| Log file not created | Ensure the process has write permission in the current directory. |

---

## License

This project is provided for educational / demonstration purposes.
