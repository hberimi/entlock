package io.tempo.hberimi.testlock;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;
import io.tempo.hberimi.locker.EntityLocker;
import io.tempo.hberimi.locker.impl.EntityLockerImpl;
import org.junit.*;
import org.junit.rules.Timeout;
import io.tempo.hberimi.testutil.WaitingDaemon;
import io.tempo.hberimi.testutil.LockingDaemon;
import io.tempo.hberimi.testutil.ExceptionCauseThread;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static io.tempo.hberimi.testutil.ThreadOps.*;

public class LockEscalationTest {
    private EntityLocker<Integer> entityLocker;
    private LockingDaemon<Integer> lockingDaemon;

    private final static int TEST_TIMEOUT = 15;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final WaitingDaemon waitingDaemon = new WaitingDaemon(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new EntityLockerImpl<>(5);
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        lockingDaemon = new LockingDaemon<>(entityLocker);
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testEscalation() {
        final CountDownLatch afterTryLock = new CountDownLatch(1);
        final ExceptionCauseThread firstSubThread = THREAD_CREATOR.startThread(() -> {
            final int startEntity = 0;
            final int lockEntities = 2;
            multiEntitiesEvaluator(lockingDaemon::lock, startEntity, lockEntities);

            mainThreadWaiter.countDown();
            waitingDaemon.await(afterTryLock);

            multiEntitiesEvaluator(entityLocker::unlock, startEntity, lockEntities);
            subThreadWaiter.countDown();
        });

        final ExceptionCauseThread secondSubThread = THREAD_CREATOR.startThread(() -> {
            waitingDaemon.await(mainThreadWaiter);
            sleep(1);
            for (int i = 0; i < 5; i++) {
                assertFalse(lockingDaemon.tryLockWithTime(i, 100, TimeUnit.MICROSECONDS));
            }
            afterTryLock.countDown();
        });

        final int startEntity = 2;
        final int lockEntities = 4;
        final int lastEntity = multiEntitiesEvaluator(lockingDaemon::lock, startEntity, lockEntities);

        waitingDaemon.await(mainThreadWaiter);
        lockingDaemon.lock(lastEntity);

        waitingDaemon.await(afterTryLock);
        waitingDaemon.await(subThreadWaiter);

        multiEntitiesEvaluator(entityLocker::unlock, startEntity, lockEntities + 1);
        waitThread(firstSubThread);
        waitThread(secondSubThread);
    }

    private int multiEntitiesEvaluator(IntConsumer entityLocker, int start, int count) {
        final int end = start + count;
        for (int i = start; i < end; ++i) {
            entityLocker.accept(i);
        }

        return end;
    }

    private void lockWithIgnoreException(int entityId) {
        try {
            entityLocker.lock(entityId);
        } catch (InterceptedDeadlockException ignore) {

        }
    }
}
