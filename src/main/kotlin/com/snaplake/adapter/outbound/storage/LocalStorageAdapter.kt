package com.snaplake.adapter.outbound.storage

import com.snaplake.application.port.outbound.StorageProvider
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class LocalStorageAdapter(
    private val basePath: String,
) : StorageProvider {

    private val root: Path = Path.of(basePath)

    override fun write(path: String, data: ByteArray) {
        val target = root.resolve(path)
        Files.createDirectories(target.parent)
        Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
    }

    override fun read(path: String): ByteArray {
        return Files.readAllBytes(root.resolve(path))
    }

    override fun list(prefix: String): List<String> {
        val dir = root.resolve(prefix)
        if (!dir.exists() || !dir.isDirectory()) return emptyList()

        return Files.walk(dir)
            .filter { Files.isRegularFile(it) }
            .map { root.relativize(it).toString() }
            .toList()
    }

    override fun delete(path: String) {
        val target = root.resolve(path)
        Files.deleteIfExists(target)
    }

    override fun deleteAll(prefix: String) {
        val dir = root.resolve(prefix)
        if (!dir.exists()) return

        Files.walk(dir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    override fun exists(path: String): Boolean {
        return root.resolve(path).exists()
    }

    override fun getUri(path: String): String {
        return root.resolve(path).toAbsolutePath().toUri().toString()
    }

    override fun testConnection(): Boolean {
        return try {
            Files.createDirectories(root)
            val testFile = root.resolve(".snaplake-test")
            Files.write(testFile, byteArrayOf(1))
            Files.deleteIfExists(testFile)
            true
        } catch (e: Exception) {
            false
        }
    }
}
