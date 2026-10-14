package com.aliothmoon.maameow.domain.service

import android.content.Context
import android.os.PowerManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

class TaskExecutionWakeLockTest {
    @Test
    fun acquireIsBoundedAndIdempotent_releaseIsIdempotent() {
        val lock = mockk<PowerManager.WakeLock>(relaxed = true)
        every { lock.isHeld } returns true
        val power = mockk<PowerManager> {
            every {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaaMeow:task-execution")
            } returns lock
        }
        val context = mockk<Context> {
            every { getSystemService(Context.POWER_SERVICE) } returns power
        }
        val holder = TaskExecutionWakeLock(context)

        holder.acquire()
        holder.acquire()
        holder.release()
        every { lock.isHeld } returns false
        holder.release()

        verify(exactly = 1) {
            power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaaMeow:task-execution")
        }
        verifyOrder {
            lock.setReferenceCounted(false)
            lock.acquire(TaskExecutionWakeLock.MAX_HOLD_MS)
            lock.release()
        }
        verify(exactly = 1) { lock.release() }
    }
}
