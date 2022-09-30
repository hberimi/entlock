package io.tempo.hberimi.testutil;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;
import io.tempo.hberimi.locker.EntityLocker;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.fail;

public class LockingDaemon<T> {
    private final EntityLocker<T> entityLocker;

    public LockingDaemon(EntityLocker<T> entityLocker) {
        this.entityLocker = entityLocker;
    }

    public void lock(T entityId) {
        try {
            entityLocker.lock(entityId);
        } catch (InterceptedDeadlockException e) {
            fail();
        }
    }

    public boolean tryLockWithTime(T entityId, int time, TimeUnit timeUnit) {
        try {
            return entityLocker.tryLock(entityId, time, timeUnit);
        } catch (InterruptedException | InterceptedDeadlockException e) {
            fail();
        }

        return false;
    }

    public boolean tryLockWithoutTime(T entityId) {
        try {
            return entityLocker.tryLock(entityId);
        } catch (InterceptedDeadlockException e) {
            fail();
        }

        return false;
    }

    public void globalLock() {
        try {
            entityLocker.globalLock();
        } catch (InterceptedDeadlockException e) {
            fail();
        }
    }

    public boolean tryGlobalLock() {
        try {
            return entityLocker.tryGlobalLock();
        } catch (InterceptedDeadlockException e) {
            fail();
        }

        return false;
    }

    public boolean tryGlobalLock(int time, TimeUnit timeUnit) {
        try {
            return entityLocker.tryGlobalLock(time, timeUnit);
        } catch (InterceptedDeadlockException e) {
            fail();
        }
        return false;
    }

    public boolean tryLock(T entityId) {
        try {
            return entityLocker.tryLock(entityId);
        } catch (InterceptedDeadlockException e) {
            fail();
        }

        return false;
    }
}
