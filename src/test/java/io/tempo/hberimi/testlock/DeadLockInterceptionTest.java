package io.tempo.hberimi.testlock;

import io.tempo.hberimi.interceptor.exception.InterceptedDeadlockException;
import io.tempo.hberimi.locker.EntityLocker;
import io.tempo.hberimi.locker.impl.EntityLockerImpl;
import org.junit.*;
import org.junit.rules.Timeout;
import io.tempo.hberimi.testutil.WaitingDaemon;
import io.tempo.hberimi.testutil.LockingDaemon;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.*;
import static io.tempo.hberimi.testutil.ThreadOps.*;

public class DeadLockInterceptionTest {
    private EntityLocker<Integer> entityLocker;
    private LockingDaemon<Integer> lockingDaemon;

    private final static int TEST_TIMEOUT = 10;
    private final static TimeUnit TEST_TIME_UNIT = TimeUnit.SECONDS;

    private final WaitingDaemon waitingDaemon = new WaitingDaemon(TEST_TIMEOUT, TEST_TIME_UNIT);
    private CountDownLatch mainThreadWaiter;
    private CountDownLatch subThreadWaiter;

    private Throwable expectedException;

    @Rule
    public Timeout testTimeout = new Timeout(TEST_TIMEOUT, TEST_TIME_UNIT);

    @Before
    public void setUp() {
        entityLocker = new EntityLockerImpl<>();
        mainThreadWaiter = new CountDownLatch(1);
        subThreadWaiter = new CountDownLatch(1);
        lockingDaemon = new LockingDaemon<>(entityLocker);
        expectedException = null;
    }

    @After
    public void checkThreadsException() throws Throwable {
        checkException();
    }

    @Test
    public void testClassicDeadlock() {
        final int[] entitiesIds = entitiesIds(2);

        final Thread subThread = THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(entitiesIds[0]);

            waitingDaemon.await(subThreadWaiter);

            lockingDaemon.lock(entitiesIds[1]);
        });

        lockingDaemon.lock(entitiesIds[1]);

        subThreadWaiter.countDown();
        sleep(1);

        if (assertRightDeadlockPrevented(entitiesIds[0], Thread.currentThread(), subThread)) {
            return;
        }

        fail();
    }

    @Test
    public void testChainDeadLock() {
        final int[] entitiesIds = entitiesIds(3);
        //For more readability
        final CountDownLatch firstSubThreadWaiter = mainThreadWaiter;
        final CountDownLatch secondSubThreadWaiter = subThreadWaiter;

        final Thread firstSubThread = THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(entitiesIds[0]);

            waitingDaemon.await(firstSubThreadWaiter);

            lockingDaemon.lock(entitiesIds[1]);
        });

        final Thread secondSubThread = THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(entitiesIds[1]);

            firstSubThreadWaiter.countDown();
            waitingDaemon.await(secondSubThreadWaiter);

            lockingDaemon.lock(entitiesIds[2]);
        });

        lockingDaemon.lock(entitiesIds[2]);
        secondSubThreadWaiter.countDown();
        sleep(1);

        final Thread currentThread = Thread.currentThread();
        assertRightDeadlockPrevented(entitiesIds[0], currentThread, firstSubThread);
        assertRightDeadlockPrevented(entitiesIds[1], currentThread, secondSubThread);
    }

    @Test
    public void testCancelGlobalLockDueDeadlock() {
        final int[] entities = entitiesIds(2);

        final Thread subThread = new Thread(() -> {
            lockingDaemon.lock(entities[0]);

            waitingDaemon.await(subThreadWaiter);

            sleep(1);

            InterceptedDeadlockException exception = null;
            try {
                entityLocker.lock(entities[1]);
            } catch (InterceptedDeadlockException e) {
                exception = e;
            }
            entityLocker.unlock(entities[0]);
            throw new RuntimeException(exception);
        });
        subThread.setUncaughtExceptionHandler((t, e) -> expectedException = e);

        subThread.start();

        lockingDaemon.lock(entities[1]);
        subThreadWaiter.countDown();
        lockingDaemon.globalLock();

        sleep(1);

        assertTrue(expectedException.getCause() instanceof InterceptedDeadlockException);
        assertTrue(lockingDaemon.tryLockWithoutTime(1));
    }

    @Test(expected = InterceptedDeadlockException.class)
    public void doubleGlobalLockWithEntitiesPrevented() throws InterceptedDeadlockException {
        final int[] entitiesIds = entitiesIds(2);

        THREAD_CREATOR.startThread(() -> {
            lockingDaemon.lock(entitiesIds[0]);
            lockingDaemon.globalLock();
        });

        lockingDaemon.lock(entitiesIds[1]);
        sleep(1);
        entityLocker.globalLock();
    }

    private boolean assertRightDeadlockPrevented(int entityId, Thread expectedFailThread, Thread expectedLockerThread) {
        try {
            entityLocker.lock(entityId);
        } catch (InterceptedDeadlockException e) {
            assertEquals(expectedFailThread, e.failThread());
            assertEquals(expectedLockerThread, e.lockerThread());
            return true;
        }

        return false;
    }

    private int[] entitiesIds(int size) {
        return IntStream.iterate(0, operand -> ++operand)
                .limit(size)
                .toArray();
    }
}
