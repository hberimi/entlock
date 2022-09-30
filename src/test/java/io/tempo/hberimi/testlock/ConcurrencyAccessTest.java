package io.tempo.hberimi.testlock;

import io.tempo.hberimi.locker.EntityLocker;
import io.tempo.hberimi.locker.impl.EntityLockerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import io.tempo.hberimi.testutil.WaitingDaemon;
import io.tempo.hberimi.testutil.LockingDaemon;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static io.tempo.hberimi.testutil.ThreadOps.*;

public class ConcurrencyAccessTest {
    private final static int TEST_TIMEOUT = 15;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;
    private LockingDaemon<Integer> lockingDaemon;

    private TestEntity mainEntity;

    private final WaitingDaemon waitingDaemon = new WaitingDaemon(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    private EntityLocker<Integer> entityLocker;

    @Before
    public void setUp() throws Exception {
        entityLocker = new EntityLockerImpl<>();
        lockingDaemon = new LockingDaemon<>(entityLocker);
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        mainEntity = new TestEntity(1);
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testSimultaneouslyRunOfOneEntity() {
        THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(mainEntity.id);
            mainEntity.value = 1;

            mainThreadWaiter.countDown();
            waitingDaemon.await(subThreadWaiter);

            entityLocker.unlock(mainEntity.id);
        });
        waitingDaemon.await(mainThreadWaiter);

        assertEquals(1, mainEntity.value);

        assertFalse(lockingDaemon.tryLockWithTime(mainEntity.id, 1, TimeUnit.SECONDS));

        subThreadWaiter.countDown();

        assertTrue(lockingDaemon.tryLockWithTime(mainEntity.id, 1, TimeUnit.SECONDS));

        mainEntity.value = 2;

        entityLocker.unlock(mainEntity.id);
        assertEquals(2, mainEntity.value);
    }

    @Test
    public void testThatThreadWaitsEntityUnlock() {
        final int expected = 3;
        THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(mainEntity.id);

            mainThreadWaiter.countDown();

            mainEntity.value = 2;

            entityLocker.unlock(mainEntity.id);
        });
        waitingDaemon.await(mainThreadWaiter);

        lockingDaemon.lock(mainEntity.id);
        mainEntity.value = expected;
        entityLocker.unlock(mainEntity.id);

        assertEquals(expected, mainEntity.value);
    }

    @Test
    public void testEvaluateConcurrentlyDifferentEntities() {
        final int singleAction = 1;
        final int threadsCount = 1_000;

        CountDownLatch waitThreadsLockEntities = new CountDownLatch(threadsCount);
        CountDownLatch allThreadsCompleteWork = new CountDownLatch(singleAction);
        CountDownLatch threadsUnlockEntities = new CountDownLatch(threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            int entityId = i;
            THREAD_CREATOR.startThread(() -> {
                lockingDaemon.lock(entityId);
                waitThreadsLockEntities.countDown();

                waitingDaemon.await(allThreadsCompleteWork);

                entityLocker.unlock(entityId);
                threadsUnlockEntities.countDown();
            });
        }
        waitingDaemon.await(waitThreadsLockEntities);

        allThreadsCompleteWork.countDown();

        waitingDaemon.await(threadsUnlockEntities);

        assertEquals(0, entityLocker.currentSize());
    }
}
