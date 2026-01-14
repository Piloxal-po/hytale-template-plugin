# Hytale Plugin Development Environment

This project is a pre-configured development environment for creating and testing Hytale plugins. It features a powerful Gradle setup that automates the process of running a local Hytale server with your plugin installed.

## Features

- **Automated Server Setup**: Finds your local Hytale installation and sets up a test server automatically.
- **Cross-Platform Support**: Automatically locates the Hytale installation on Windows, macOS, and Linux.
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

Open the newly created `local.properties` file. You will need to fill in your details depending on the authentication mode you choose.

### 3. Run the Server Again

Now, run the same command again:

```bash
./gradlew runServer
```

The server will start. If you are using `authenticated` mode, follow the on-screen instructions to authorize the server via your web browser.

---

## ⚙️ Configuration (`local.properties`)

This file gives you full control over the test server's behavior.

-   `hytale.owner.name` / `hytale.owner.uuid`
    -   Your Hytale username and UUID.
    -   **Required** only if `hytale.singleplayer.enabled` is `true`.

-   `hytale.version`
    -   The game version folder to use from your Hytale installation (e.g., `latest`, `dev`).
    -   Default: `latest`

-   `hytale.server.port`
    -   The network port the server will listen on.
    -   Default: `5520`

-   `hytale.auth.mode`
    -   `authenticated`: (Default) Requires online authentication.
    -   `offline`: For local/LAN play. Requires `singleplayer.enabled=true` and owner information.
    -   `insecure`: A developer-only mode that may not be compatible with release clients.

-   `hytale.singleplayer.enabled`
    -   `true`: Runs the server as a singleplayer world. Required for `offline` mode.
    -   `false`: (Default) Runs the server as a multiplayer instance.

-   `hytale.server.autoCommand`
    -   A command to automatically run when the server has booted.
    -   Default: `/auth login device` (when `auth.mode` is `authenticated`). Leave blank to disable.

-   `hytale.jvm.args`
    -   Extra arguments for the Java Virtual Machine (JVM), like memory allocation.
    -   Example: `-Xmx4G -Dsome.flag=true`

-   `hytale.server.extraArgs`
    -   Extra command-line arguments to pass directly to `HytaleServer.jar`.
    -   Example: `--some-new-option value --another-option`

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
