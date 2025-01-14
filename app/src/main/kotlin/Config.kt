@file:OptIn(ExperimentalHoplite::class)

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.sksamuel.hoplite.ConfigAlias
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.PropertySource
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.isDirectory

private fun validateDirectoryPath(path: Path, errorMessage: String) =
    if (!path.isDirectory()) throw IllegalArgumentException(errorMessage) else Unit

// easier with custom enum versus reuse of logback level and custom decoder
enum class LogLevel {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF;
}

data class AppConfig(
    @param:ConfigAlias("repo")
    val repoPath: Path,
    @param:ConfigAlias("output")
    val outputPath: Path,
    @param:ConfigAlias("log")
    val level: LogLevel = LogLevel.INFO
)

fun validateConfig(args: Array<String>): Result<AppConfig> = runCatching {
    val config = ConfigLoader.builder()
        .addPropertySource(PropertySource.commandLine(args))
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<AppConfig>()

    val errorMessage: (String) -> String = {destination -> "Path to $destination directory is not a directory" }

    validateDirectoryPath(config.repoPath, errorMessage("repo"))
    validateDirectoryPath(config.outputPath, errorMessage("output"))

    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    rootLogger.level = Level.valueOf(config.level.toString())

    config
}