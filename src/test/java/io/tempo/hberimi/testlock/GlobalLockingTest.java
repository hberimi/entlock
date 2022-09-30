package io.tempo.hberimi.testlock;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;
import io.tempo.hberimi.locker.EntityLocker;
import io.tempo.hberimi.locker.impl.EntityLockerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import io.tempo.hberimi.testutil.WaitingDaemon;
import io.tempo.hberimi.testutil.LockingDaemon;
import io.tempo.hberimi.testutil.ThreadOps;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static io.tempo.hberimi.testutil.ThreadOps.*;

public class GlobalLockingTest {
    private EntityLocker<Integer> entityLocker;
    private LockingDaemon<Integer> lockingDaemon;

    private final static int TEST_TIMEOUT = 300;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final WaitingDaemon waitingDaemon = new WaitingDaemon(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;
    private TestEntity mainEntity;
    private TestEntity subEntity;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new EntityLockerImpl<>();
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        mainEntity = new TestEntity(1);
        subEntity = new TestEntity(2);
        lockingDaemon = new LockingDaemon<>(entityLocker);
    }

    @After
    public void checkThreadsException() throws Throwable {
        ThreadOps.checkException();
    }

    @Test
    public void testGlobalLocking() {
        final int time = 1;
        final TimeUnit second = TimeUnit.SECONDS;
        THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(subThreadWaiter);
            assertFalse(lockingDaemon.tryLockWithTime(mainEntity.id, time, second));
        });

        THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(subThreadWaiter);
            assertFalse(lockingDaemon.tryLockWithTime(subEntity.id, time, second));
        });

        lockingDaemon.globalLock();

        subThreadWaiter.countDown();

        sleep(2);

        entityLocker.globalUnlock();

        lockingDaemon.globalLock();
        entityLocker.globalUnlock();
    }

    @Test
    public void testGlobalLockingCorrectlyWorksWithOtherThreads() {
        final int expected = 3;
        THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(mainEntity.id);
            mainThreadWaiter.countDown();

            mainEntity.value = 2;
            sleep(2);

            entityLocker.unlock(mainEntity.id);
        });

        final Thread secondThread = THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(subThreadWaiter);
            lockingDaemon.lock(subEntity.id);

            subEntity.value = 2;

            entityLocker.unlock(subEntity.id);
        });

        waitingDaemon.await(mainThreadWaiter);

        lockingDaemon.globalLock();
        subThreadWaiter.countDown();

        mainEntity.value = expected;
        subEntity.value = expected;

        entityLocker.globalUnlock();

        waitThread(secondThread);

        assertEquals(expected, mainEntity.value);
        assertEquals(expected - 1, subEntity.value);
    }

    @Test
    public void testTryGlobalLocking() {
        THREAD_CREATOR.startThread(() -> {
            assertTrue(lockingDaemon.tryGlobalLock(1, TimeUnit.SECONDS));

            mainThreadWaiter.countDown();
            waitingDaemon.await(subThreadWaiter);

            entityLocker.globalUnlock();
        });

        waitingDaemon.await(mainThreadWaiter);

        assertFalse(lockingDaemon.tryGlobalLock(1, TimeUnit.SECONDS));
        subThreadWaiter.countDown();

        assertTrue(lockingDaemon.tryGlobalLock(1, TimeUnit.SECONDS));
        entityLocker.globalUnlock();
    }

    @Test
    public void testGlobalLockAllowLockEntitiesFromTheSameThread() {
        THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(2);

            mainThreadWaiter.countDown();
            sleep(1);
            lockingDaemon.lock(1);
            sleep(2);
            entityLocker.unlock(1);
            entityLocker.unlock(2);
        });
        THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(subThreadWaiter);
            lockingDaemon.lock(3);
            entityLocker.unlock(3);
        });
        waitingDaemon.await(mainThreadWaiter);
        lockingDaemon.globalLock();

        subThreadWaiter.countDown();

        assertTrue(lockingDaemon.tryLock(1));
        assertTrue(lockingDaemon.tryLock(2));

        subThreadWaiter.countDown();
        sleep(1);

        entityLocker.globalUnlock();
    }

    @Test(expected = InterceptedDeadlockException.class)
    public void cannotAcquireTwoGlobalLocksIfThreadsLocksEntities() throws InterceptedDeadlockException {
        THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(subThreadWaiter);
            lockingDaemon.globalLock();
            mainThreadWaiter.countDown();
        });

        lockingDaemon.lock(1);
        subThreadWaiter.countDown();
        sleep(1);
        entityLocker.globalLock();
    }
}
