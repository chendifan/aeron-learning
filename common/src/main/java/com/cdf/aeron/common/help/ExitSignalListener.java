package com.cdf.aeron.common.help;

import org.agrona.concurrent.ShutdownSignalBarrier;

/**
 * @author chendifan
 * @date 2024-09-02
 */
public class ExitSignalListener {
    private static final ShutdownSignalBarrier BARRIER = new ShutdownSignalBarrier();

    public static void awaitShutdown() {
        BARRIER.await();
    }

    public static void signalShutdown() {
        BARRIER.signal();
    }
}
