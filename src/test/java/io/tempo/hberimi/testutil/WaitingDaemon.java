package io.tempo.hberimi.testutil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

public class WaitingDaemon {
    private final int timeOut;
    private final TimeUnit timeUnit;

    public WaitingDaemon(int timeOut, TimeUnit timeUnit) {
        this.timeOut = timeOut;
        this.timeUnit = timeUnit;
    }

    public void await(CountDownLatch countDownLatch) {
        try {
            countDownLatch.await(timeOut, timeUnit);
        } catch (InterruptedException e) {
            fail();
        }
    }
}
