package com.aliothmoon.maameow.utils

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class InputTooLargeException(val limitBytes: Int) : IOException("input exceeds $limitBytes bytes")

/** 用户选的文件可能选错成大文件，整份读进内存会 OOM */
fun InputStream.readTextAtMost(limitBytes: Int): String {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        if (out.size() + n > limitBytes) throw InputTooLargeException(limitBytes)
        out.write(buffer, 0, n)
    }
    return out.toString(Charsets.UTF_8.name())
}
