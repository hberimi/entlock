package io.tempo.hberimi.locker.impl;

import io.tempo.hberimi.util.SimpleLogger;
import io.tempo.hberimi.util.Counter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

class LockEscalator {
    private final Map<Thread, Counter> lockedEntitiesByThread;

    private final AtomicInteger totalLocks;
    private final int minLocksBeforeGlobal;
    private Thread escalatedThread;

    public LockEscalator(final int minLocksBeforeGlobal) {
        this.minLocksBeforeGlobal = minLocksBeforeGlobal;
        this.lockedEntitiesByThread = new ConcurrentHashMap<>();
        this.totalLocks = new AtomicInteger();
    }
    public boolean incThreadEntityCounter() {
        final Thread currentThread = Thread.currentThread();
        Counter counter = lockedEntitiesByThread.get(currentThread);

        if (counter == null) {
            counter = new Counter();
            lockedEntitiesByThread.put(currentThread, counter);
        } else {
            counter.inc();
        }

        totalLocks.incrementAndGet();

        return acquireEscalatedThread(isNeedEscalation(counter.count()));
    }

    private synchronized boolean acquireEscalatedThread(final boolean isNeedEscalation) {
        if (isNeedEscalation && escalatedThread == null) {
            escalatedThread = Thread.currentThread();
            return true;
        }

        return false;
    }

    public boolean decThreadEntityCounter() {
        final Thread currentThread = Thread.currentThread();

        final Counter counter = lockedEntitiesByThread.get(currentThread);

        counter.dec();

        if (counter.count() == 0) {
            lockedEntitiesByThread.remove(currentThread);
        }

        totalLocks.decrementAndGet();

        return deescalateThread(!isNeedEscalation(counter.count()));
    }

    private synchronized boolean deescalateThread(final boolean isNeedDeescalation) {
        return isNeedDeescalation && escalatedThread == Thread.currentThread();
    }

    public synchronized void cancelEscalation() {
        escalatedThread = null;
    }

    public int currentThreadLockedEntities() {
        final Counter counter = lockedEntitiesByThread.get(Thread.currentThread());
        return counter == null ? 0 : counter.count();
    }

    private boolean isNeedEscalation(final int count) {
        final int allLocks = totalLocks.get();
        SimpleLogger.logDebug("Total lock/Current Thread locks = " + allLocks + "/" + count);
        return count >= minLocksBeforeGlobal && (count > (allLocks >> 1));
    }
}
