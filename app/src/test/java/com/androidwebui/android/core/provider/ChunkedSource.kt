package com.androidwebui.android.core.provider

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer

/** Delivers [data] in pieces of at most [chunkSize] bytes, like a slow network. */
internal class ChunkedSource(private val data: ByteArray, private val chunkSize: Int, private val failAtEnd: Boolean = false) : Source {
    private var position = 0

    override fun read(sink: Buffer, byteCount: Long): Long {
        if (position >= data.size) {
            if (failAtEnd) throw java.io.IOException("connection reset")
            return -1
        }
        val n = minOf(chunkSize.toLong(), byteCount, (data.size - position).toLong()).toInt()
        sink.write(data, position, n)
        position += n
        return n.toLong()
    }

    override fun timeout() = Timeout.NONE

    override fun close() = Unit
}

internal fun chunked(text: String, chunkSize: Int, failAtEnd: Boolean = false) =
    ChunkedSource(text.toByteArray(Charsets.UTF_8), chunkSize, failAtEnd).buffer()
