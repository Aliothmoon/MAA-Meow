package com.aliothmoon.maameow.manager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Root 拉起的命令：进 init 的挂载命名空间、修回 root 建的目录，两者都要能退回原样 */
class RootSpawnCommandTest {

    @Test
    fun suSpawner_entersInitMountNamespace_withFallback() {
        val command = SuSpawner.wrapCommand("/x/liblauncher.so", "'/x/liblauncher.so' --token='t'")
        assertTrue(command.startsWith("if nsenter -t 1 -m -- true 2>/dev/null; then "))
        assertTrue(command.contains("nsenter -t 1 -m -- '/x/liblauncher.so' --token='t' >/dev/null 2>&1 &"))
        // nsenter 不可用时照旧后台拉起
        assertTrue(command.contains("else '/x/liblauncher.so' --token='t' >/dev/null 2>&1 & fi"))
    }

    @Test
    fun ownershipRepair_onlyRunsAsRoot_andQuotesPath() {
        val command = shellOwnershipRepairCommand("/data/local/tmp/it's")
        assertTrue(command.startsWith("[ \"$(id -u)\" = 0 ] && "))
        assertTrue(command.contains("find '/data/local/tmp/it'\"'\"'s' ! -user 2000 -exec chown -h 2000:2000 {} +"))
    }

    @Test
    fun findProcesses_leavesShellExpansionToShell() {
        assertEquals(
            "p=$(pidof 'pkg:svc') && ps -o PID,ETIME -p \${p// /,}",
            findProcessesCommand("pkg:svc"),
        )
    }

    @Test
    fun elevatedProcessName_carriesUserIdOutsideMainUser() {
        assertEquals("pkg:svc", elevatedProcessName("pkg", "svc", 10343))
        // 分身与主用户同名会互相当残留杀
        assertEquals("pkg:svc_u999", elevatedProcessName("pkg", "svc", 99910343))
    }

    @Test
    fun parseProcesses_skipsHeaderAndKeepsElapsed() {
        assertEquals(
            listOf(ResidualProcess(42, "00:17"), ResidualProcess(7, "1-02:03:04")),
            parseProcesses(listOf("  PID     ELAPSED", "   42       00:17", "    7  1-02:03:04", "")),
        )
    }
}
