package io.tempo.hberimi.interceptor.exception;

import static io.tempo.hberimi.util.SimpleLogger.logError;

public class InterceptedDeadlockException extends Exception {
    private final Thread lockerThread;
    private final Thread failThread;

    public InterceptedDeadlockException(String message, Thread failThread, Thread lockedThread) {
        super(message);

        logError(message);

        this.lockerThread = lockedThread;
        this.failThread = failThread;
    }

    public Thread lockerThread() {
        return lockerThread;
    }

    public Thread failThread() {
        return failThread;
    }
}
