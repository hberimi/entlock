package io.tempo.hberimi.util;

import lombok.extern.log4j.Log4j;

import java.util.concurrent.TimeUnit;

@Log4j
public class SimpleLogger {

    private SimpleLogger() {
        throw new UnsupportedOperationException();
    }

    public static void logDebug(final Object message) {
        if (log.isDebugEnabled()) {
            log.debug(message);
        }
    }

    public static void logDebugCurrentThread(final Object message) {
        if (log.isDebugEnabled()) {
            log.debug(Thread.currentThread() + " " + message);
        }
    }

    public static void logError(final Object message) {
        if (log.isDebugEnabled()) {
            log.error(message);
        }
    }

    public static String waitTimeToString(final long timeout, final TimeUnit timeUnit) {
        return timeout + " " + timeUnit;
    }
}
