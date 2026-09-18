# DevPulse: Server & API Health Monitoring CLI

Hey there! 👋 Welcome to **DevPulse**, a lightweight, pure Java command-line tool I built to monitor the health of servers and APIs. 

No messy dependencies, no heavy frameworks—just a fast, easy-to-use terminal interface to make sure your endpoints are up and running.

---

## What does it do?

If you've ever wondered, *"Is my website down?"* or *"Why is my API so slow?"*, DevPulse is the tool for you. 

- **Ping Multiple URLs at Once**: Checks all your saved websites at the exact same time so you don't have to wait.
- **Track Latency**: Tells you exactly how fast (or slow) your servers are responding in milliseconds.
- **SLA & Uptime Stats**: Keeps track of how often your sites are succeeding vs failing over time.
- **Log Errors**: If a server goes down, it automatically saves the details to a log file so you can check what went wrong later.
- **100% Pure Java**: Built using just the standard Java library. 

---

## Folder Structure

Here's how the project is organized:

- **`src/`** — All the Java code lives here
- **`data/`** — Where your saved URLs are stored
- **`logs/`** — Error logs get saved here
- **`assets/`** — Screenshots for this page
- **`run.bat`** — Just double-click to start!

---

## How to Run It

You'll need **Java 11 or higher** installed on your computer.

### The Easy Way (Windows)
Just double-click the `run.bat` file! It will compile the code and launch the app for you automatically.

### The Manual Way (Terminal)
If you prefer doing things by hand, open your terminal and type:

```text
javac -d bin src/com/devpulse/*/*.java
java -cp bin com.devpulse.main.DevPulseApp
```

---

## Screenshots

Check out what the app looks like in action:

### 1. Main Menu
![Main Menu](assets/main_menu.png)

### 2. List of Saved Endpoints
![List Endpoints](assets/list_endpoints.png)

### 3. Adding a New URL
![Add Endpoint](assets/add_endpoint.png)

### 4. Updating an Endpoint
![Update Endpoint](assets/update_endpoint.png)

### 5. Running the Health Check
![Concurrent Health Check](assets/health_check.png)

---

Enjoy using DevPulse! Let me know if you run into any bugs or have any ideas to make it better.
