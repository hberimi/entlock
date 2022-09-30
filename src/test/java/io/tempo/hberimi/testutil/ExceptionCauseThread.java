package io.tempo.hberimi.testutil;

public class ExceptionCauseThread extends Thread {
    private Throwable exception;

    public ExceptionCauseThread(Runnable target) {
        super(target);
    }

    public Throwable exception() {
        return exception;
    }

    @Override
    public void run() {
        try {
            super.run();
        } catch (Throwable e) {
            exception = e;
        }
    }
}
