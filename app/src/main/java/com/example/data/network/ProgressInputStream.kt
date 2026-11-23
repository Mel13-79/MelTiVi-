package com.example.data.network

import java.io.FilterInputStream
import java.io.InputStream

/**
 * Поток-обёртка, подсчитывающий прочитанные байты и вызывающий коллбэк прогресса
 * (прочитано байт из общего количества). Используется для отображения прогресса загрузки EPG.
 */
class ProgressInputStream(
    inputStream: InputStream,
    private val totalBytes: Long,
    private val onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
) : FilterInputStream(inputStream) {

    private var bytesRead: Long = 0L
    private var lastReported: Long = 0L

    override fun read(): Int {
        val value = super.read()
        if (value >= 0) {
            bytesRead++
            maybeReport()
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = super.read(b, off, len)
        if (count > 0) {
            bytesRead += count
            maybeReport()
        }
        return count
    }

    override fun skip(n: Long): Long {
        val skipped = super.skip(n)
        if (skipped > 0) {
            bytesRead += skipped
            maybeReport()
        }
        return skipped
    }

    private fun maybeReport() {
        if (bytesRead - lastReported >= 64 * 1024L || bytesRead >= totalBytes) {
            lastReported = bytesRead
            onProgress(bytesRead, totalBytes)
        }
    }

    val currentBytesRead: Long get() = bytesRead
}
