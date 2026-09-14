import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// ======================================================================
//  Concurrent Seat Booking System - single-file version
//  Everything lives in this one file so it's easy to submit/compile.
//  Only "Main" is public (Java allows only one public top-level class
//  per file, and it must match the file name).
// ======================================================================

enum SeatStatus {
    AVAILABLE,
    BOOKED
}

// ---- Custom exceptions -------------------------------------------------

class InvalidSeatException extends Exception {
    public InvalidSeatException(String message) {
        super(message);
    }
}

class SeatAlreadyBookedException extends Exception {
    public SeatAlreadyBookedException(String message) {
        super(message);
    }
}

class SeatNotBookedException extends Exception {
    public SeatNotBookedException(String message) {
        super(message);
    }
}

class UnauthorizedCancellationException extends Exception {
    public UnauthorizedCancellationException(String message) {
        super(message);
    }
}

// ---- Domain classes ------------------------------------------------------

class Seat {

    private final int seatId;
    // volatile so a read of status/bookedBy from any thread is guaranteed to
    // see the latest write, even if that thread isn't going through one of
    // Show's synchronized methods. Show's monitor is still what prevents two
    // threads from booking the same seat at once (mutual exclusion); volatile
    // here only fixes visibility, it does not replace that locking.
    private volatile SeatStatus status;
    private volatile String bookedBy;

    public Seat(int seatId) {
        this.seatId = seatId;
        this.status = SeatStatus.AVAILABLE;
        this.bookedBy = null;
    }

    public int getSeatId() {
        return seatId;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public String getBookedBy() {
        return bookedBy;
    }

    // Still only ever called from within Show's synchronized methods, so
    // mutual exclusion on the *decision* to book is unaffected -- this just
    // makes the resulting writes visible immediately to any other reader.
    public void markBooked(String userName) {
        this.status = SeatStatus.BOOKED;
        this.bookedBy = userName;
    }

    public void markAvailable() {
        this.status = SeatStatus.AVAILABLE;
        this.bookedBy = null;
    }

    @Override
    public String toString() {
        String base = "Seat " + seatId + " -> " + status;
        if (bookedBy != null) {
            base += " (booked by " + bookedBy + ")";
        }
        return base;
    }
}

class Booking {

    private final int seatId;
    private final String userName;
    private final String showName;
    private final LocalDateTime bookedAt;

    public Booking(int seatId, String userName, String showName) {
        this.seatId = seatId;
        this.userName = userName;
        this.showName = showName;
        this.bookedAt = LocalDateTime.now();
    }

    public int getSeatId() {
        return seatId;
    }

    public String getUserName() {
        return userName;
    }

    public String getShowName() {
        return showName;
    }

    public LocalDateTime getBookedAt() {
        return bookedAt;
    }

    @Override
    public String toString() {
        return "Booking[show=" + showName + ", seat=" + seatId +
                ", user=" + userName + ", time=" + bookedAt + "]";
    }
}

class Show {

    private final String showName;
    private final Map<Integer, Seat> seats;

    public Show(String showName, int totalSeats) {
        this.showName = showName;
        this.seats = new LinkedHashMap<>();
        for (int i = 1; i <= totalSeats; i++) {
            seats.put(i, new Seat(i));
        }
    }

    public String getShowName() {
        return showName;
    }

    /**
     * Only one thread can execute this method on a given Show object at a
     * time, so two users can never both see a seat as AVAILABLE and both
     * book it (a classic race condition).
     */
    public synchronized void bookSeat(int seatId, String userName)
            throws InvalidSeatException, SeatAlreadyBookedException {

        Seat seat = seats.get(seatId);

        if (seat == null) {
            throw new InvalidSeatException(
                    "Seat " + seatId + " does not exist for show '" + showName + "'.");
        }

        if (seat.getStatus() == SeatStatus.BOOKED) {
            throw new SeatAlreadyBookedException(
                    "Seat " + seatId + " is already booked by " + seat.getBookedBy() + ".");
        }

        // Small artificial delay so the race condition is easy to
        // demonstrate if you temporarily remove "synchronized" and re-run.
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        seat.markBooked(userName);
    }

    /**
     * Cancels a booking. Also synchronized for the same reason as
     * bookSeat() -- we don't want a book and a cancel racing on the same
     * seat and leaving it in an inconsistent state.
     */
    public synchronized void cancelBooking(int seatId, String userName)
            throws InvalidSeatException, SeatNotBookedException, UnauthorizedCancellationException {

        Seat seat = seats.get(seatId);

        if (seat == null) {
            throw new InvalidSeatException(
                    "Seat " + seatId + " does not exist for show '" + showName + "'.");
        }

        if (seat.getStatus() == SeatStatus.AVAILABLE) {
            throw new SeatNotBookedException(
                    "Seat " + seatId + " is not currently booked, nothing to cancel.");
        }

        if (!seat.getBookedBy().equals(userName)) {
            throw new UnauthorizedCancellationException(
                    userName + " cannot cancel seat " + seatId +
                            " because it was booked by " + seat.getBookedBy() + ".");
        }

        seat.markAvailable();
    }

    public synchronized int getAnyBookedSeatId() {
        for (Seat seat : seats.values()) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                return seat.getSeatId();
            }
        }
        return -1;
    }

    public synchronized String getBookedByForSeat(int seatId) {
        Seat seat = seats.get(seatId);
        return seat != null ? seat.getBookedBy() : null;
    }

    public synchronized void printSeatMap() {
        System.out.println("\n--- Seat map for " + showName + " ---");
        for (Seat seat : seats.values()) {
            System.out.println(seat);
        }
    }
}

// ---- Logging (I/O streams) ------------------------------------------------

class BookingLogger {

    private final String logFilePath;

    public BookingLogger(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    // append = true so every thread's log line gets added, not overwritten.
    // synchronized so two threads don't interleave their writes.
    public synchronized void log(String message) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFilePath, true))) {
            writer.write("[" + LocalDateTime.now() + "] " + message);
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }
}

// ---- Persistence (JDBC) ----------------------------------------------------

class DBManager {

    // ---- Update these three to match your own database setup ----
    private static final String URL = "jdbc:mysql://localhost:3306/booking_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "your_password";
    // ---------------------------------------------------------------

    private Connection connection;

    public void connect() throws SQLException {
        connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
        createTableIfNotExists();
    }

    private void createTableIfNotExists() throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS bookings (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "show_name VARCHAR(150), " +
                "seat_id INT, " +
                "user_name VARCHAR(100), " +
                "booked_at DATETIME)";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public synchronized void saveBooking(Booking booking) {
        String sql = "INSERT INTO bookings (show_name, seat_id, user_name, booked_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, booking.getShowName());
            ps.setInt(2, booking.getSeatId());
            ps.setString(3, booking.getUserName());
            ps.setObject(4, booking.getBookedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to save booking to DB: " + e.getMessage());
        }
    }

    public synchronized void removeBooking(String showName, int seatId) {
        String sql = "DELETE FROM bookings WHERE show_name = ? AND seat_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, showName);
            ps.setInt(2, seatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to remove booking from DB: " + e.getMessage());
        }
    }

    public List<String> fetchAllBookings() {
        List<String> results = new ArrayList<>();
        String sql = "SELECT show_name, seat_id, user_name, booked_at FROM bookings ORDER BY booked_at";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                results.add(rs.getString("show_name") + " | Seat " + rs.getInt("seat_id") +
                        " | " + rs.getString("user_name") + " | " + rs.getTimestamp("booked_at"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch bookings: " + e.getMessage());
        }
        return results;
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Failed to close DB connection: " + e.getMessage());
        }
    }
}

// ---- Concurrency tasks -----------------------------------------------------

class UserBookingTask implements Runnable {

    private final Show show;
    private final int seatId;
    private final String userName;
    private final BookingLogger logger;
    private final DBManager dbManager; // may be null if DB is not connected

    public UserBookingTask(Show show, int seatId, String userName,
                            BookingLogger logger, DBManager dbManager) {
        this.show = show;
        this.seatId = seatId;
        this.userName = userName;
        this.logger = logger;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        try {
            show.bookSeat(seatId, userName);
            Booking booking = new Booking(seatId, userName, show.getShowName());

            System.out.println(userName + " -> SUCCESS: booked seat " + seatId);
            logger.log("SUCCESS - " + userName + " booked seat " + seatId +
                    " for " + show.getShowName());

            if (dbManager != null) {
                dbManager.saveBooking(booking);
            }

        } catch (SeatAlreadyBookedException | InvalidSeatException e) {
            System.out.println(userName + " -> FAILED for seat " + seatId + ": " + e.getMessage());
            logger.log("FAILED - " + userName + " tried seat " + seatId + " -> " + e.getMessage());
        }
    }
}

class CancelBookingTask implements Runnable {

    private final Show show;
    private final int seatId;
    private final String userName;
    private final BookingLogger logger;
    private final DBManager dbManager;

    public CancelBookingTask(Show show, int seatId, String userName,
                              BookingLogger logger, DBManager dbManager) {
        this.show = show;
        this.seatId = seatId;
        this.userName = userName;
        this.logger = logger;
        this.dbManager = dbManager;
    }

    @Override
    public void run() {
        try {
            show.cancelBooking(seatId, userName);
            System.out.println(userName + " -> CANCELLED seat " + seatId);
            logger.log("CANCELLED - " + userName + " cancelled seat " + seatId +
                    " for " + show.getShowName());

            if (dbManager != null) {
                dbManager.removeBooking(show.getShowName(), seatId);
            }

        } catch (InvalidSeatException | SeatNotBookedException | UnauthorizedCancellationException e) {
            System.out.println(userName + " -> CANCEL FAILED for seat " + seatId + ": " + e.getMessage());
            logger.log("CANCEL FAILED - " + userName + " tried to cancel seat " + seatId +
                    " -> " + e.getMessage());
        }
    }
}

// ---- Entry point ------------------------------------------------------------

public class Main {

    public static void main(String[] args) throws InterruptedException {

        int totalSeats = 5;
        Show show = new Show("Avengers: Doomsday - 7:00 PM", totalSeats);
        BookingLogger logger = new BookingLogger("booking_log.txt");

        // Database persistence is optional. If MySQL is not set up / running,
        // the program still works correctly using only in-memory + file logging.
        DBManager dbManager = new DBManager();
        boolean dbAvailable = true;
        try {
            dbManager.connect();
        } catch (SQLException e) {
            System.out.println("DB not available, continuing without persistence: " + e.getMessage());
            dbAvailable = false;
        }

        System.out.println("=== Booking Phase: 10 users competing for 5 seats ===");

        // 10 users competing for only 5 seats -> guarantees some collisions,
        // which is exactly what proves the synchronization is working.
        int numUsers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(numUsers);
        Random random = new Random();
        List<String> userNames = new ArrayList<>();
        for (int i = 1; i <= numUsers; i++) {
            userNames.add("User" + i);
        }

        for (String user : userNames) {
            int seatChoice = random.nextInt(totalSeats) + 1; // seats numbered 1..totalSeats
            pool.execute(new UserBookingTask(show, seatChoice, user, logger,
                    dbAvailable ? dbManager : null));
        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        show.printSeatMap();

        // ---- Cancellation demo -------------------------------------------
        System.out.println("\n=== Cancellation Phase ===");

        int bookedSeat = show.getAnyBookedSeatId();
        if (bookedSeat != -1) {
            String owner = show.getBookedByForSeat(bookedSeat);

            // 1) The actual owner cancels successfully.
            new CancelBookingTask(show, bookedSeat, owner, logger,
                    dbAvailable ? dbManager : null).run();

            // 2) Someone tries to cancel that same seat again -> now it's
            //    available, so this should fail with SeatNotBookedException.
            new CancelBookingTask(show, bookedSeat, "RandomUser", logger,
                    dbAvailable ? dbManager : null).run();
        }

        // 3) A different user tries to cancel someone else's booking ->
        //    UnauthorizedCancellationException.
        int anotherBookedSeat = show.getAnyBookedSeatId();
        if (anotherBookedSeat != -1) {
            new CancelBookingTask(show, anotherBookedSeat, "SomeoneElse", logger,
                    dbAvailable ? dbManager : null).run();
        }

        // 4) Cancelling a seat number that doesn't exist -> InvalidSeatException.
        new CancelBookingTask(show, 999, "User1", logger,
                dbAvailable ? dbManager : null).run();

        show.printSeatMap();

        if (dbAvailable) {
            System.out.println("\n--- Bookings currently stored in DB ---");
            for (String row : dbManager.fetchAllBookings()) {
                System.out.println(row);
            }
            dbManager.close();
        }

        System.out.println("\nFull success/failure/cancel log written to booking_log.txt");
    }
}
