# Hytale Plugin Development Environment

This project is a pre-configured development environment for creating and testing Hytale plugins. It features a powerful Gradle setup that automates the process of running a local Hytale server with your plugin installed.

## Features

- **Automated Server Setup**: Finds your local Hytale installation and sets up a test server automatically.
- **Dynamic Configuration**: Control server behavior via a simple `local.properties` file.
- **One-Command Execution**: Run a single command to build your plugin and launch the server.
- **Fast Iteration**: By default, the server environment is preserved for faster startups.
- **CI/CD Ready**: Includes a GitHub Actions workflow for automated builds.

---

## 🚀 Quick Start

### Prerequisites

1.  **Java 21 JDK**: Ensure you have a Java 21 JDK installed and configured.
2.  **Hytale Game**: You must have the Hytale game installed on your machine.

### 1. First-Time Launch

The first time you run the server, a `local.properties` file will be created for you.

```bash
# For Windows
gradlew.bat runServer

# For macOS/Linux
./gradlew runServer
```

The server will likely fail on this first run, which is **normal**. The script will create a `local.properties` file at the root of the project.

### 2. Configure `local.properties`

Open the newly created `local.properties` file. It will look like this:

```properties
# --- Player Information (for singleplayer/offline mode) ---
hytale.owner.name=
hytale.owner.uuid=

# --- Hytale Version ---
hytale.version=latest

# --- Server Startup Settings ---
hytale.auth.mode=authenticated
hytale.singleplayer.enabled=false
```

- **If you plan to use `authenticated` mode (recommended):** You don't need to change anything at first.
- **If you plan to use `offline` mode:**
    1. Set `hytale.auth.mode=offline`.
    2. Set `hytale.singleplayer.enabled=true`.
    3. **Fill in `hytale.owner.name` and `hytale.owner.uuid`** with your Hytale username and UUID.

### 3. Run the Server Again

Now, run the same command again:

```bash
./gradlew runServer
```

The server will start.

### 4. Authenticate (for `authenticated` mode)

If you are using the default `authenticated` mode, the script will automatically send the `/auth login device` command to the server. In your console, you will see a message like this:

```
[INFO] [AbstractCommand] ===================================================================
[INFO] [AbstractCommand] DEVICE AUTHORIZATION
[INFO] [AbstractCommand] ===================================================================
[INFO] [AbstractCommand] Visit: https://oauth.accounts.hytale.com/oauth2/device/verify
[INFO] [AbstractCommand] Enter code: LTMqxw4a
[INFO] [AbstractCommand] Or visit: https://oauth.accounts.hytale.com/oauth2/device/verify?user_code=LTMqxw4a
[INFO] [AbstractCommand] ===================================================================
```

1.  Click the link provided (`.../verify?user_code=...`).
2.  Authorize the device in your web browser.
3.  Once authorized, you can connect to the server from your Hytale client.

---

## ⚙️ Configuration (`local.properties`)

This file gives you full control over the test server's behavior.

-   `hytale.owner.name` / `hytale.owner.uuid`
    -   Your Hytale username and UUID.
    -   **Required** only if `hytale.singleplayer.enabled` is `true`.

-   `hytale.version`
    -   The game version folder to use from your Hytale installation.
    -   Default: `latest`

-   `hytale.auth.mode`
    -   `authenticated`: (Default) Requires online authentication. The script will help you with this.
    -   `offline`: For local/LAN play. Requires `singleplayer.enabled=true` and owner information.
    -   `insecure`: A developer-only mode that may not be compatible with release clients.

-   `hytale.singleplayer.enabled`
    -   `true`: Runs the server as a singleplayer world. Required for `offline` mode.
    -   `false`: (Default) Runs the server as a multiplayer instance.

---

## 🛠️ Advanced Usage

### Forcing a Clean Run

By default, the script reuses the existing server files in the `run/` directory for faster startups. To delete everything and start from a clean slate (e.g., after a game update), use the `-PcleanRun` project property:

```bash
./gradlew runServer -PcleanRun
```

### Debugging the Server

To start the server with a Java Debug Wire Protocol (JDWP) agent, allowing you to attach a debugger, use the `-Pdebug` project property:

```bash
./gradlew runServer -Pdebug
```

The server will wait for a debugger to connect on **port 5005**.

---

## 📦 Plugin Development

-   Your plugin's source code is located in `src/main/java`.
-   To build the plugin JAR without running the server, use the `shadowJar` task:

```bash
./gradlew shadowJar
```

The final JAR will be located in `build/libs/`.
