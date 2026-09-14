Problem Statement - Create a single-file Java console program that models the backend functionality of the seat booking system for just one show at a time. The system must accurately prevent double booking of seats. Concurrency must be implemented so that two or more users cannot reserve the same seat even during peak load. Concurrency must be implemented using Java primitives such as synchronized and volatile for seat booking and cancellation functions. The console program must offer a textual user interface that allows user input so that a user can view what seats are available and can book seats or cancel bookings. Data integrity must be maintained and the system must not allow the possibility that a user could check the seat availability and book the same seat at the same time as someone else is also doing this. All activity around booking operations must be accurately logged and optionally persisted to storage so that historical records of all bookings is available.
Scope of the Project - Here is the Scope of the Project:

Scope of the Project

It is a single-file Java application that simulates the core backend of a seat booker that can only handle a single show booking at a time․ It implements:

Seat booking and canceling are thread-safe‚ using Java concurrency primitives like synchronized and volatile․
Interactive console menu for inspecting seats‚ booking and cancelling booking․
An automated demo of how concurrency doesn't cause race conditions by having a pool of threads simulate multiple users trying to access a limited number of seats․
Persistent logging to a local text file of every booking/cancellation attempt attempted․
Optional MySQL database support via JDBC to persist successful bookings‚ along with graceful fallback to in-memory booking if the database is not configured or reachable․

This project is not going to have a GUI‚ web front-end‚ user login‚ payment processing‚ concurrent support for multiple shows‚ or a REST/network API․ It is a simple and fully contained project that only needs to get concurrency handling and backend booking logic correct‚ not a full-stack production-ready booking platform․
