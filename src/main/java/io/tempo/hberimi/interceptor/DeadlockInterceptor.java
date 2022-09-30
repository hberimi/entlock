package io.tempo.hberimi.interceptor;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;

import java.util.*;

public class DeadlockInterceptor<T> {
    private final Map<T, Thread> lockedEntities;
    private final Map<Thread, T> waitingThreads;
    private final Map<Thread, Set<T>> threadLockedEntities;
    private Thread globalThread;

    public DeadlockInterceptor() {
        this.lockedEntities = new HashMap<>();
        this.waitingThreads = new HashMap<>();
        this.threadLockedEntities = new HashMap<>();
    }
    public synchronized void beforeLocking(final T entityId) throws InterceptedDeadlockException {
        checkOnDeadlock(entityId);

        waitingThreads.put(Thread.currentThread(), entityId);
    }

    public synchronized void beforeGlobalLocking() throws InterceptedDeadlockException {
        final Thread currentGlobalThread = Thread.currentThread();

        if (globalThread != null && globalThread != currentGlobalThread) {
            if (lockAnyEntity()) {
                final String message = globalDeadlockPreventedMessage(currentGlobalThread, globalThread);
                throw new InterceptedDeadlockException(message, currentGlobalThread, globalThread);
            }
        } else {
            globalThread = currentGlobalThread;
        }

        for (final Map.Entry<Thread, T> entry : waitingThreads.entrySet()) {

            final Thread lockedThread = lockedEntities.get(entry.getValue());

            if (lockedThread == currentGlobalThread) {
                final Thread failThread = entry.getKey();
                final String message = globalDeadlockPreventedMessage(failThread, currentGlobalThread);
                throw new InterceptedDeadlockException(message, failThread, currentGlobalThread);
            }
        }

        //puts null as indicator that we wait all other threads
        waitingThreads.put(currentGlobalThread, null);
    }

    public synchronized void beforeGlobalUnlocking() {
        waitingThreads.remove(globalThread);
        globalThread = null;
    }

    public synchronized void afterLocking(final T entityId, final boolean isLocked) {
        final Thread currentThread = Thread.currentThread();

        waitingThreads.remove(currentThread);
        if (isLocked) {
            lockedEntities.put(entityId, currentThread);

            Set<T> threadEntities = threadLockedEntities.get(currentThread);

            if (threadEntities == null) {
                threadEntities = new HashSet<>();
            }

            threadEntities.add(entityId);

            threadLockedEntities.putIfAbsent(currentThread, threadEntities);
        }
    }

    private boolean lockAnyEntity() {
        return threadLockedEntities.get(Thread.currentThread()) != null;
    }

    public synchronized void beforeUnlocking(final T entityId) {
        final Thread currentThread = Thread.currentThread();
        final Set<T> threadEntities = threadLockedEntities.get(currentThread);

        threadEntities.remove(entityId);

        if (threadEntities.isEmpty()) {
            threadLockedEntities.remove(currentThread);
        }

        lockedEntities.remove(entityId);
    }
    private void checkOnDeadlock(T entityId) throws InterceptedDeadlockException {
        final T originEntity = entityId;
        final Thread currentThread = Thread.currentThread();

        while (lockedEntities.containsKey(entityId)) {

            final Thread entityThread = lockedEntities.get(entityId);
            entityId = waitingThreads.get(entityThread);

            if (entityThread == currentThread || entityThread == globalThread) {
                final Thread lockerThread = lockedEntities.get(originEntity);
                final String message = deadlockPreventedMessage(currentThread, originEntity, lockerThread);
                throw new InterceptedDeadlockException(message, currentThread, lockerThread);
            }
        }
    }

    private String globalDeadlockPreventedMessage(final Thread failThread, final Thread lockedThread) {
        return "Thread {" + failThread + "} unable to acquire global lock due case of deadlock." +
                " Entity pending by {" + lockedThread + "}";
    }

    private String deadlockPreventedMessage(final Thread failThread, final T originEntity, final Thread lockedThread) {
        return "Thread {" + failThread + "} unable to lock entity {" + originEntity + "} due case of deadlock." +
                " Entity pending by {" + lockedThread + "}";
    }
}
