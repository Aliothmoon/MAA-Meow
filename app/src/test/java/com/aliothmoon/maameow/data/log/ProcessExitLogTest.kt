package com.aliothmoon.maameow.data.log

import com.aliothmoon.maameow.remote.ProcessExitRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ProcessExitLogTest {

    private fun record(
        timestamp: Long,
        pid: Int = 100,
        reason: Int = 3,
        reasonName: String = "LOW_MEMORY",
        status: Int = 9,
        description: String? = null,
    ) = ProcessExitRecord(
        timestamp = timestamp,
        pid = pid,
        processName = "com.example",
        reason = reason,
        reasonName = reasonName,
        detail = 0,
        detailName = "",
        status = status,
        importance = 125,
        pssKb = 1024L * 1024,
        rssKb = 0,
        description = description,
    )

    @Test
    fun firstRun_backfillsOnlyLatestFew() {
        val records = (1L..6L).map { record(timestamp = it * 1000) }.shuffled()

        val fresh = ProcessExitLog.selectNew(records, watermark = null)

        assertEquals(listOf(4000L, 5000L, 6000L), fresh.map { it.timestamp })
    }

    @Test
    fun afterWatermark_onlyNewerAreSelected() {
        val records = listOf(record(1000), record(2000), record(3000))

        val fresh = ProcessExitLog.selectNew(records, ProcessExitLog.Watermark(2000, 100))

        assertEquals(listOf(3000L), fresh.map { it.timestamp })
    }

    @Test
    fun repeatedStartWithoutNewDeath_selectsNothing() {
        val records = listOf(record(1000), record(2000))
        val watermark = ProcessExitLog.selectNew(records, null).last().let { ProcessExitLog.Watermark(it.timestamp, it.pid) }

        assertTrue("反复启动不得重复记录", ProcessExitLog.selectNew(records, watermark).isEmpty())
    }

    @Test
    fun sameMillisecond_isOrderedByPidAndNotRepeated() {
        val records = listOf(record(2000, pid = 7), record(2000, pid = 3))

        val first = ProcessExitLog.selectNew(records, ProcessExitLog.Watermark(1000, 0))
        assertEquals(listOf(3, 7), first.map { it.pid })

        val again = ProcessExitLog.selectNew(records, ProcessExitLog.Watermark(2000, 7))
        assertTrue(again.isEmpty())
    }

    @Test
    fun expected_updatesCleanExitAndTaskForceStop() {
        assertTrue(ProcessExitLog.isExpected(ExitSubject.SELF, record(1, reason = 16, reasonName = "PACKAGE_UPDATED")))
        assertTrue(ProcessExitLog.isExpected(ExitSubject.SELF, record(1, reason = 1, reasonName = "EXIT_SELF", status = 0)))
        assertTrue(
            ProcessExitLog.isExpected(
                ExitSubject.SELF,
                record(1, reason = 10, reasonName = "USER REQUESTED", description = "stop com.example due to installPackageLI"),
            )
        )
        assertTrue(
            ProcessExitLog.isExpected(
                ExitSubject.GAME,
                record(1, reason = 10, reasonName = "USER_REQUESTED", description = "stop com.hypergryph.arknights due to from pid 4993"),
            )
        )
        // MIUI 的写法
        assertTrue(
            ProcessExitLog.isExpected(
                ExitSubject.GAME,
                record(1, reason = 10, reasonName = "USER REQUESTED", description = "stop com.hypergryph.arknights due to from process:8818"),
            )
        )
    }

    @Test
    fun unexpected_killsCrashesAndUserForceStopOfApp() {
        assertFalse(ProcessExitLog.isExpected(ExitSubject.SELF, record(1)))
        // MIUI 锁屏清理：系统杀的，必须 WARN
        assertFalse(
            ProcessExitLog.isExpected(
                ExitSubject.GAME,
                record(1, reason = 10, reasonName = "USER REQUESTED", description = "stop com.hypergryph.arknights due to AutoLockOffClean"),
            )
        )
        assertFalse(ProcessExitLog.isExpected(ExitSubject.SELF, record(1, reason = 1, reasonName = "EXIT_SELF", status = 1)))
        // App 自身被强行停止要 WARN：排查时据此排除「用户自己关的」
        assertFalse(
            ProcessExitLog.isExpected(
                ExitSubject.SELF,
                record(1, reason = 10, reasonName = "USER_REQUESTED", description = "stop com.example due to from pid 1"),
            )
        )
    }

    @Test
    fun format_containsReasonImportanceMemoryAndDescription() {
        val line = ProcessExitLog.format(
            ExitSubject.GAME,
            record(0, pid = 42, description = "oplus kill"),
            zone = ZoneId.of("UTC"),
        )

        assertEquals(
            "Process exit [game] com.example pid=42 at 01-01 00:00:00.000 reason=LOW_MEMORY(3) status=9 " +
                "importance=FOREGROUND_SERVICE(125) pss=1024.0MB desc=\"oplus kill\"",
            line,
        )
    }

    @Test
    fun parseNames_readsHiddenSubreasonFromToString() {
        val raw = "ApplicationExitInfo(timestamp=10/17/26 0:51 pid=8790 realUid=10123 packageUid=10123 " +
            "definingUid=10123 user=0 process=com.aliothmoon.maameow reason=3 (LOW_MEMORY) " +
            "subreason=5 (TOO MANY EMPTY) status=0 importance=125 pss=1.1GB rss=1.4GB description=null " +
            "state=empty trace=null)"

        val names = ProcessExitRecord.parseNames(raw)

        assertEquals("LOW_MEMORY", names.reasonName)
        assertEquals(5, names.detail)
        assertEquals("TOO MANY EMPTY", names.detailName)
    }
}
