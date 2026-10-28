package com.aliothmoon.maameow.root;

import com.aliothmoon.maameow.third.Ln;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * 提权进程的统一退出口：先起硬期限再清理，清理卡死也一定退出，不留孤儿
 * 用 halt 不用 System.exit：不重跑 shutdown hook，也不会被 hook 卡住
 */
public final class ProcessExit {

    private static final String TAG = "ProcessExit";
    public static final long DEADLINE_MS = 5_000L;

    private static final AtomicBoolean exiting = new AtomicBoolean(false);
    private static volatile Thread owner;

    // 测试替换，免得真把 JVM 停了、白等期限
    static volatile IntConsumer halt = code -> Runtime.getRuntime().halt(code);
    static volatile long deadlineMs = DEADLINE_MS;

    private ProcessExit() {
    }

    /**
     * 首个调用者起期限、清理、halt；其他线程的后来者直接返回，期限已在走
     * 清理途中同一线程再进来（如 App 死亡回调转 destroy）就地执行，外层负责 halt
     */
    public static void exit(String reason, Runnable cleanup) {
        if (!exiting.compareAndSet(false, true)) {
            if (Thread.currentThread() == owner) {
                runCleanup(reason, cleanup);
            } else {
                Ln.i(TAG + ": already exiting, ignore " + reason);
            }
            return;
        }
        owner = Thread.currentThread();
        Ln.i(TAG + ": exiting, reason=" + reason);
        long budget = deadlineMs;
        Thread deadline = new Thread(() -> {
            try {
                Thread.sleep(budget);
            } catch (InterruptedException ignored) {
                return;
            }
            Ln.e(TAG + ": cleanup exceeded " + budget + "ms, halting, reason=" + reason);
            halt.accept(1);
        }, "exit-deadline");
        deadline.setDaemon(true);
        deadline.start();
        runCleanup(reason, cleanup);
        deadline.interrupt();
        halt.accept(0);
    }

    private static void runCleanup(String reason, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (Throwable tr) {
            Ln.e(TAG + ": cleanup failed, reason=" + reason, tr);
        }
    }

    static void resetForTest() {
        exiting.set(false);
        owner = null;
    }
}
