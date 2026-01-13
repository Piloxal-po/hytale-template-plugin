import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.util.Properties

/**
 * Custom Gradle plugin for automated Hytale server testing.
 * This plugin automatically finds the Hytale installation on the user's machine
 * and configures the server based on 'local.properties'.
 */
open class RunHytalePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val javaToolchains = project.extensions.getByType(JavaToolchainService::class.java)
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }

        project.tasks.register("runServer", RunServerTask::class.java) {
            javaLauncher.set(launcher)
            group = "hytale"
            description = "Finds the local Hytale installation and runs the server with your plugin"
            dependsOn("shadowJar")
        }
    }
}

abstract class RunServerTask : DefaultTask() {

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @TaskAction
    fun run() {
        // --- Load Local Properties ---
        val config = loadLocalConfig()

        // --- Find Hytale Installation ---
        val hytaleInstallDir = findHytaleGameDir(config.version)
        if (hytaleInstallDir == null || !hytaleInstallDir.exists()) {
            throw IllegalStateException("Hytale installation not found for version '${config.version}'. Searched in: .../Hytale/install/release/package/game/${config.version}")
        }
        println("Found Hytale installation for version '${config.version}' at: $hytaleInstallDir")

        val sourceAssetsZip = File(hytaleInstallDir, "Assets.zip")
        val sourceServerJar = File(hytaleInstallDir, "Server/HytaleServer.jar")

        if (!sourceAssetsZip.exists()) throw IllegalStateException("Assets.zip not found in Hytale installation: ${sourceAssetsZip.path}")
        if (!sourceServerJar.exists()) throw IllegalStateException("HytaleServer.jar not found in Hytale installation: ${sourceServerJar.path}")

        // --- Setup Run Directory ---
        val rootRunDir = File(project.projectDir, "run")
        val serverDir = File(rootRunDir, "server")
        val modsDir = File(serverDir, "mods")

        // Conditionally clean the run directory if -PcleanRun is passed
        if (project.hasProperty("cleanRun")) {
            println("Cleaning up previous run due to -PcleanRun flag...")
            if (rootRunDir.exists()) {
                rootRunDir.deleteRecursively()
            }
        }
        
        serverDir.mkdirs()
        modsDir.mkdirs()
        
        // --- Copy Game Files if they don't exist ---
        val destAssetsZip = File(modsDir, "Assets.zip")
        val destServerJar = File(serverDir, "HytaleServer.jar")

        if (!destAssetsZip.exists()) {
            println("Assets.zip not found in run directory. Copying from local Hytale installation...")
            sourceAssetsZip.copyTo(destAssetsZip)
            println("Assets.zip copied.")
        } else {
            println("Assets.zip already exists. Skipping copy.")
        }

        if (!destServerJar.exists()) {
            println("HytaleServer.jar not found in run directory. Copying from local Hytale installation...")
            sourceServerJar.copyTo(destServerJar)
            println("HytaleServer.jar copied.")
        } else {
            println("HytaleServer.jar already exists. Skipping copy.")
        }

        // --- Always copy the latest plugin version ---
        val pluginsDir = File(serverDir, "plugins").apply { mkdirs() }
        project.tasks.findByName("shadowJar")?.outputs?.files?.firstOrNull()?.let { shadowJar ->
            shadowJar.copyTo(File(pluginsDir, shadowJar.name), overwrite = true)
            println("Plugin copied to: ${pluginsDir.absolutePath}/${shadowJar.name}")
        } ?: println("WARNING: Could not find shadowJar output. Make sure the 'shadow' plugin is applied.")

        // --- Build Server Arguments ---
        val serverArgs = buildServerArgs(config)
        
        // --- Start Server ---
        println("Starting Hytale server with args: ${serverArgs.joinToString(" ")}")
        println("Press Ctrl+C to stop the server")

        val jvmArgs = mutableListOf<String>()
        if (project.hasProperty("debug")) {
            jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
            println("Debug mode enabled. Connect debugger to port 5005")
        }

        val processArgs = jvmArgs + listOf("-jar", "HytaleServer.jar") + serverArgs

        val process = ProcessBuilder(javaLauncher.get().executablePath.asFile.absolutePath, *processArgs.toTypedArray())
            .directory(serverDir)
            .start()

        setupGracefulShutdown(process)
        forwardProcessOutput(process, config)

        val exitCode = process.waitFor()
        println("Server exited with code $exitCode")
    }

    private fun loadLocalConfig(): ServerConfig {
        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")

        if (!localPropertiesFile.exists()) {
            println("local.properties not found. Creating a default file.")
            println("Please review the generated 'local.properties' and fill in your details if needed.")
            
            val defaultConfig = """
                # ==========================================
                # Hytale Server Local Configuration
                # ==========================================
                # This file allows you to customize the server startup without editing the build scripts.
                # It is ignored by Git to keep your personal settings private.

                # --- Player Information (for singleplayer/offline mode) ---
                # Your Hytale username and UUID.
                # Required when singleplayer.enabled=true to identify you as the owner.
                hytale.owner.name=
                hytale.owner.uuid=

                # --- Hytale Version ---
                # The game version folder to use from your Hytale installation.
                # (e.g., latest, dev, etc.)
                hytale.version=latest

                # ==========================================
                # Server Startup Settings
                # ==========================================

                # Authentication Mode
                # Defines how the server handles player authentication.
                # Possible values:
                #   - offline: No online authentication. Best for LAN/local play.
                #   - insecure: Development mode. Disables all security checks.
                #   - authenticated: (Default) Requires players to be authenticated by official Hytale servers.
                hytale.auth.mode=authenticated

                # Singleplayer Mode
                # Set to 'true' to run the server in singleplayer mode.
                # This is often required for 'offline' mode to work correctly with the client.
                # Possible values: true, false
                hytale.singleplayer.enabled=false
            """.trimIndent()
            
            localPropertiesFile.writeText(defaultConfig)
        }

        FileInputStream(localPropertiesFile).use { properties.load(it) }
        
        return ServerConfig(properties)
    }

    private fun buildServerArgs(config: ServerConfig): List<String> {
        val args = mutableListOf<String>()
        args.add("--auth-mode")
        args.add(config.authMode)

        if (config.singleplayerEnabled) {
            args.add("--singleplayer")
            args.add("--owner-name")
            args.add(config.ownerName)
            args.add("--owner-uuid")
            args.add(config.ownerUuid)
        }
        return args
    }

    private fun findHytaleGameDir(version: String): File? {
        val appData = System.getenv("APPDATA") ?: return null
        return File(appData, "Hytale/install/release/package/game/$version")
    }

    private fun setupGracefulShutdown(process: Process) {
        project.gradle.buildFinished {
            if (process.isAlive) {
                println("\nStopping server...")
                process.destroy()
            }
        }
    }

    private fun forwardProcessOutput(process: Process, config: ServerConfig) {
        // Handle server's standard output
        Thread {
            var commandSent = false
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    println(line) // Print the server log line
                    // When server has booted and we are in authenticated mode, send the command
                    if (!commandSent && config.authMode == "authenticated" && line.contains("Hytale Server Booted!")) {
                        println("Server has booted. Automatically sending '/auth login device' command...")
                        try {
                            process.outputStream.write(("/auth login device\n").toByteArray())
                            process.outputStream.flush()
                            println("Command sent. Please follow the authentication steps that appear below.")
                            commandSent = true
                        } catch (e: IOException) {
                            System.err.println("Error sending automatic command: ${e.message}")
                        }
                    }
                }
            }
        }.start()

        // Handle server's error output
        Thread { process.errorStream.bufferedReader().useLines { it.forEach(System.err::println) } }.start()
        
        // Handle forwarding user input to the server
        Thread {
            try {
                System.`in`.bufferedReader().useLines { lines ->
                    lines.forEach {
                        process.outputStream.write((it + "\n").toByteArray())
                        process.outputStream.flush()
                    }
                }
            } catch (e: IOException) {
                // This can happen when the process is destroyed, it's normal.
            }
        }.start()
    }
}

/**
 * Data class to hold server configuration loaded from local.properties.
 */
private class ServerConfig(properties: Properties) {
    val version: String = properties.getProperty("hytale.version", "latest")
    val authMode: String = properties.getProperty("hytale.auth.mode", "authenticated")
    val singleplayerEnabled: Boolean = properties.getProperty("hytale.singleplayer.enabled", "false").toBoolean()
    val ownerName: String
    val ownerUuid: String

    init {
        if (singleplayerEnabled) {
            ownerName = properties.getProperty("hytale.owner.name")
                ?: throw IllegalStateException("hytale.owner.name is required in local.properties when hytale.singleplayer.enabled=true")
            ownerUuid = properties.getProperty("hytale.owner.uuid")
                ?: throw IllegalStateException("hytale.owner.uuid is required in local.properties when hytale.singleplayer.enabled=true")
        } else {
            ownerName = ""
            ownerUuid = ""
        }
    }
}
