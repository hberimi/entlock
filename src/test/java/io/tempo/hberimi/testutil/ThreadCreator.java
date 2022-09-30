package io.tempo.hberimi.testutil;

import java.util.ArrayList;
import java.util.List;

public class ThreadCreator {
    private final String threadPrefix;
    private int threadCount;
    private final List<ExceptionCauseThread> runnedThreads;

    public ThreadCreator(String threadPrefix) {
        this.threadPrefix = threadPrefix;
        this.threadCount = 0;
        runnedThreads = new ArrayList<>();
    }

    public ExceptionCauseThread startThread(Runnable runnable) {
        final ExceptionCauseThread thread = new ExceptionCauseThread(runnable);
        thread.setName(createName());
        runnedThreads.add(thread);
        thread.start();
        return thread;
    }

    public List<ExceptionCauseThread> runnedThreads() {
        final List<ExceptionCauseThread> runnedThreads = new ArrayList<>(this.runnedThreads);
        this.runnedThreads.clear();
        return runnedThreads;
    }

    private String createName() {
        return threadPrefix + "-" + threadCount++;
    }
}
