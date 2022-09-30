package io.tempo.hberimi.util;

@FunctionalInterface
public interface BooleanFunction<T> {
    boolean apply(T value);
}
