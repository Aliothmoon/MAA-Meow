package com.aliothmoon.maameow.data.datasource

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

class DownloadStreamCopierTest {

    /** 永远读得到数据的流，模拟没下完的下载 */
    private class EndlessInputStream(private val reads: AtomicInteger) : InputStream() {
        override fun read(): Int = 1

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            reads.incrementAndGet()
            b.fill(1, off, off + len)
            return len
        }
    }

    private object NullOutputStream : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    @Test
    fun `取消协程时拷贝循环立刻退出`() = runBlocking {
        val reads = AtomicInteger(0)
        val job = launch(Dispatchers.IO) {
            EndlessInputStream(reads).copyWithProgress(
                output = NullOutputStream,
                total = 0L,
                bufferSize = 8 * 1024,
                onProgress = {},
            )
        }

        // 等循环真的转起来再取消
        while (reads.get() < 5) Thread.yield()

        // 循环里没有取消检查的话，这里会一直挂到超时
        withTimeout(5_000) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
    }

    @Test
    fun `正常读到 EOF 时数据完整`() = runBlocking {
        val data = ByteArray(300 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()

        ByteArrayInputStream(data).copyWithProgress(
            output = output,
            total = data.size.toLong(),
            bufferSize = 64 * 1024,
            onProgress = {},
        )

        assertArrayEquals(data, output.toByteArray())
    }
}
