package io.tempo.hberimi.testutil;

import java.util.List;

import static org.junit.Assert.fail;

public class ThreadOps {
    public static final ThreadCreator THREAD_CREATOR = new ThreadCreator("subThread");

    private ThreadOps() {
        throw new UnsupportedOperationException();
    }

    public static void sleep(double sec) {
        try {
            Thread.sleep((long) (sec * 1000));
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
    }

    public static void waitThread(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void checkException() throws Throwable {
        final List<ExceptionCauseThread> exceptionCauseThreads = THREAD_CREATOR.runnedThreads();
        for (int i = 0; i < exceptionCauseThreads.size(); ++i) {
            final Throwable exception = exceptionCauseThreads.get(i).exception();
            if (exception != null) {
                throw exception;
            }
        }
    }
}
