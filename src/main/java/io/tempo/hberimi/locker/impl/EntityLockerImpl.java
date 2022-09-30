package io.tempo.hberimi.locker.impl;

import io.tempo.hberimi.interceptor.DeadlockInterceptor;
import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;
import io.tempo.hberimi.locker.EntityLocker;
import io.tempo.hberimi.util.BooleanFunction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.tempo.hberimi.util.SimpleLogger.logDebugCurrentThread;
import static io.tempo.hberimi.util.SimpleLogger.logError;

public class EntityLockerImpl<T> implements EntityLocker<T> {
    private final Map<T, ReentrantLock> entityLocks;
    private final DeadlockInterceptor<T> deadlockInterceptor;
    private final LockEscalator lockEscalator;
    private final ReentrantReadWriteLock globalLock;
    private final ReentrantReadWriteLock nonGlobalLock;
    private int globalLocks;

    public EntityLockerImpl() {
        this(100);
    }

    public EntityLockerImpl(final int minLocksBeforeGlobal) {
        this.entityLocks = new ConcurrentHashMap<>();
        this.deadlockInterceptor = new DeadlockInterceptor<>();
        this.lockEscalator = new LockEscalator(minLocksBeforeGlobal);
        globalLock = new ReentrantReadWriteLock();
        nonGlobalLock = new ReentrantReadWriteLock();
    }

    @Override
    public void lock(final T entityId) throws InterceptedDeadlockException {
        lock(entityId, waitingLock());
    }

    @Override
    public boolean tryLock(final T entityId) throws InterceptedDeadlockException {
        return lock(entityId, Lock::tryLock);
    }


    @Override
    public boolean tryLock(final T entityId, final long timeout, final TimeUnit timeUnit) throws InterceptedDeadlockException {
        return lock(entityId, silentTryLockFunction(timeout, timeUnit));
    }

    @Override
    public void unlock(final T entityId) {
        final ReentrantLock entityLock = entityLocks.get(entityId);

        if (entityLock == null) {
            final String message = "There is no locks for entity {" + entityId + "}";
            logError(message);
            throw new IllegalMonitorStateException(message);
        }

        final Thread currentThread = Thread.currentThread();
        if (!entityLock.isHeldByCurrentThread()) {
            final String message = currentThread + " cannot unlock entity - {" + entityId + "} because it's hold by other thread";
            logError(message);
            throw new IllegalAccessError(message);
        }

        if (entityLock.getHoldCount() == 1) {
            logDebugCurrentThread("It's last lock for entity {" + entityId + "} removing it");
            deadlockInterceptor.beforeUnlocking(entityId);
            entityLocks.remove(entityId);
        } else {
            logDebugCurrentThread("unlock entity {" + entityId + "} current hold count is " + (entityLock.getHoldCount() - 1));
        }

        nonGlobalLock.readLock().unlock();
        entityLock.unlock();

        if (lockEscalator.decThreadEntityCounter()) {
            logDebugCurrentThread("deescalate global lock");
            globalUnlock();
        }
    }

    @Override
    public boolean isLockedByCurrentThread(final T entityId) {
        final ReentrantLock entityLock = entityLocks.get(entityId);

        return entityLock != null && entityLock.isHeldByCurrentThread() || globalLock.isWriteLockedByCurrentThread();
    }

    @Override
    public void globalLock() throws InterceptedDeadlockException {
        globalLock(waitingLock());
    }

    @Override
    public boolean tryGlobalLock() throws InterceptedDeadlockException {
        return globalLock(Lock::tryLock);
    }

    @Override
    public boolean tryGlobalLock(final long timeout, final TimeUnit unit) throws InterceptedDeadlockException {
        deadlockInterceptor.beforeGlobalLocking();

        final long nanos = unit.toNanos(timeout);

        final long start = System.nanoTime();
        if (!silentTryLockWithNanos(this.globalLock::writeLock, nanos)) {
            return false;
        }
        final long end = System.nanoTime();

        unlockReadLock();

        final boolean isLockGranted = silentTryLockWithNanos(nonGlobalLock::writeLock, nanos - (end - start));

        restoreReadLock();

        return isLockGranted;
    }

    @Override
    public void globalUnlock() {
        logDebugCurrentThread("release global lock");
        globalLocks = 0;
        deadlockInterceptor.beforeGlobalUnlocking();
        lockEscalator.cancelEscalation();
        nonGlobalLock.writeLock().unlock();
        globalLock.writeLock().unlock();
    }

    @Override
    public int currentSize() {
        return entityLocks.size();
    }
    private boolean lock(final T entityId, final BooleanFunction<Lock> lockFunction) throws InterceptedDeadlockException {
        logDebugCurrentThread("try gain lock for entity {" + entityId + "}");
        logDebugCurrentThread("check global lock");

        if (lockEscalator.currentThreadLockedEntities() == 0) {
            if (!lockFunction.apply(globalLock.readLock())) {
                return false;
            }

            //Here we lock both to guaranteed that no one else will locked global write lock
            nonGlobalLock.readLock().lock();
            globalLock.readLock().unlock();
        } else {
            nonGlobalLock.readLock().lock();
        }

        final ReentrantLock entityLock;
        try {
            entityLock = getEntityLock(entityId);
        } catch (InterceptedDeadlockException e) {
            nonGlobalLock.readLock().unlock();
            throw e;
        }

        if (entityLock.isLocked() && !entityLock.isHeldByCurrentThread()) {
            logDebugCurrentThread("waiting lock for entity {" + entityId + "}");
        }

        final boolean isLockGranted = lockFunction.apply(entityLock);

        if (isLockGranted) {
            logDebugCurrentThread("gain lock for entity {" + entityId + "}");
        } else {
            logDebugCurrentThread("cannot gain lock for entity {" + entityId + "}");
        }

        entityLocks.putIfAbsent(entityId, entityLock);

        afterLocking(entityId, isLockGranted);

        return isLockGranted;
    }

    private boolean globalLock(final BooleanFunction<Lock> lockFunction) throws InterceptedDeadlockException {
        deadlockInterceptor.beforeGlobalLocking();

        logDebugCurrentThread("waiting global lock");
        if (!lockFunction.apply(globalLock.writeLock())) {
            return false;
        }

        unlockReadLock();

        logDebugCurrentThread("waiting other threads completion for acquiring global lock");
        final boolean isLockGranted = lockFunction.apply(nonGlobalLock.writeLock());
        logDebugCurrentThread("acquire global lock");

        restoreReadLock();

        return isLockGranted;
    }
    private boolean silentTryLockWithNanos(final Supplier<Lock> lock, final long timeout) {
        try {
            return lock.get().tryLock(timeout, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private BooleanFunction<Lock> silentTryLockFunction(final long timeout, final TimeUnit timeUnit) {
        return lock -> {
            try {
                return lock.tryLock(timeout, timeUnit);
            } catch (InterruptedException e) {
                return false;
            }
        };
    }

    private void afterLocking(final T entityId, final boolean isLockGranted) throws InterceptedDeadlockException {
        deadlockInterceptor.afterLocking(entityId, isLockGranted);
        callEscalationIfNeeds(isLockGranted);
    }

    private void callEscalationIfNeeds(final boolean isLockGranted) throws InterceptedDeadlockException {
        if (isLockGranted && lockEscalator.incThreadEntityCounter()) {
            logDebugCurrentThread("start promotion to global lock due escalation");
            globalLock();
        }
    }

    private ReentrantLock getEntityLock(final T entityId) throws InterceptedDeadlockException {
        final ReentrantLock existingLock = existingLock(entityId);

        if (existingLock.isLocked() && !existingLock.isHeldByCurrentThread()) {
            deadlockInterceptor.beforeLocking(entityId);
        }

        return existingLock;
    }

    private void restoreReadLock() {
        nonGlobalReadLockEvaluator(Lock::lock);
    }

    private void unlockReadLock() {
        globalLocks = lockEscalator.currentThreadLockedEntities();
        nonGlobalReadLockEvaluator(Lock::unlock);
    }

    private BooleanFunction<Lock> waitingLock() {
        return lock -> {
            lock.lock();
            return true;
        };
    }

    private void nonGlobalReadLockEvaluator(Consumer<Lock> locker) {
        for (int i = 0; i < globalLocks; ++i) {
            locker.accept(nonGlobalLock.readLock());
        }
    }

    private ReentrantLock existingLock(final T entityId) {
        return entityLocks.computeIfAbsent(entityId, t -> new ReentrantLock());
    }
}
