package no.nav.common.embeddedutils

import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID

public class ArgsHolder {
    public companion object {
        lateinit var args : Array<String>
        var requests = 0
    }
}

/**
 * A function returning the next available socket port.
 * Note: This method has been modified to return port from cmd line, if specified. We only return the
 *   predefined port from the 2nd call (for Kafka brokers, not for Zookeeper), see KafkaEnvironment.kt
 */
fun getAvailablePort(): Int = ServerSocket(0).run {
    reuseAddress = true
    close()
    ArgsHolder.requests ++
    if (ArgsHolder.args.size >= ArgsHolder.requests)
        return Integer.parseInt(ArgsHolder.args[ArgsHolder.requests - 1])
    localPort
}

fun deleteDir(dir: Path) {
    if (Files.exists(dir)) {
        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
    }
}

private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"))

fun appDirFor(appName: String): Path = tmpDir.resolve(appName)

fun dataDirFor(path: Path): Path = path.resolve(UUID.randomUUID().toString()).apply {
    Files.createDirectories(this)
}
