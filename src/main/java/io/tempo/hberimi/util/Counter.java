package io.tempo.hberimi.util;

public class Counter {
    private int count;

    public Counter() {
        this.count = 1;
    }

    public void inc() {
        ++count;
    }

    public void dec() {
        --count;
    }

    public int count() {
        return count;
    }
}
