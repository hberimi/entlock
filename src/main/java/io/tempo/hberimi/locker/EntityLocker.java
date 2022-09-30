package io.tempo.hberimi.locker;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;

import java.util.concurrent.TimeUnit;


public interface EntityLocker<T> {
    void lock(T entityId) throws InterceptedDeadlockException;

    boolean tryLock(T entityId) throws InterceptedDeadlockException;

    boolean tryLock(T entityId, long timeout, TimeUnit unit) throws InterruptedException, InterceptedDeadlockException;

    void unlock(T entityId);

    boolean isLockedByCurrentThread(T entityId);

    void globalLock() throws InterceptedDeadlockException;

    boolean tryGlobalLock() throws InterceptedDeadlockException;

    boolean tryGlobalLock(long timeout, TimeUnit unit) throws InterceptedDeadlockException;

    void globalUnlock();
    int currentSize();
}
