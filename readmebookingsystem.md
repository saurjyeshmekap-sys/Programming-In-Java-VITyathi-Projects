Concurrent Seat Reservation System

A terminal-based, single-file Java application that simulates the seat reservation system of a movie/show with thread-safe concurrency control and the possibility of using MySQL for persistence. It shows the way of safely handling many users who want to reserve same seats at the same time and ensuring that no double bookings are present in the system.

Table of Contents

Overview

Features

Project Structure

Prerequisites

Setup Instructions

Database Configuration (Optional)

Running the Application

Using the Application

About the Concurrency Demonstration

Output Files

Troubleshooting

How It Works Internally

Overview

This project simulates a rather simple cinema-seat reservation system for a single "show" (for example, a movie screening). You can do the following:

View color-coded seat map in terminal;

Reserve and cancel seats;

Run an automated stress test when 10 simulated users try to reserve 5 seats at the same time and verify that the system never allows two users reserve same seat.

With the colored seat grid you will want to configure your terminal to support ANSI colors. Make sure to follow the instructions in the previous chapter to verify whether you have a terminal supporting ANSI colors, but just to remind you: if your operating system is some version of Windows, Mac OS X, or Linux from the last decade or so, you probably will have a terminal that supports ANSI colors. Some really old versions of Windows that ship with cmd.exe tend not to support color, but the program will work fine anyway, just without the colors.

MySQL Server is nice to have when you need to persist your bookings in a proper database. The program can work without it (it will store its state in memory and in a log file as plain text), but the full functionality is only available when you have set up MySQL Server. You will also need a suitable JDBC driver (MySQL Connector/J), which can be downloaded in the configuration section.

If you have the project in a version control system, such as Git, you need to check it out—or "clone" it, as that operation is called in Git terminology. Suppose you have a Git repository containing this project. You can clone it to your computer and move to the folder:

git clone &lt;your-repository-url&gt;

cd &lt;repository-folder&gt;

Where &lt;your-repository-url&gt; is a valid URL to your repository and &lt;repository-folder&gt; is the name of the folder where the repository files will be downloaded. You need to open a terminal window in that location. If you don't have the project in a version control system, you can just place the file Main.java in an empty folder and open a terminal in there.

If you wish to analyze the output in finer detail after the program has run, you may want to see the contents of its log file, located in the programs working directory as booking_log.txt. This file contains information about attempts to book or cancel, including timestamps that may aid in identifying concurrency issues.

During application run, you may receive the following messages on the console:

1\. "javac: command not found": This occurs when the JDK is not installed or not in your PATH. To fix this, install the JDK and re-open your command shell.

2\. "ANSI: \[36m": Your terminal does not support color escape sequences; this is indicative only and will not impact the functioning of booking, so if this is a problem switch to a modern terminal application.

3\. "DB not available, continuing without persistence": This message will occur if either MySQL is not running or if the login credentials for DBManager are incorrect. It informs you that booking is proceeding without database persistence; you may ignore this message.

4\. If you receive an error message such as "ClassNotFoundException" from MySQL, it indicates the JDBC driver JAR is not in your classpath. To check the JAR is present, refer back to the entry concerning Step "E" above, which details the -cp options used.