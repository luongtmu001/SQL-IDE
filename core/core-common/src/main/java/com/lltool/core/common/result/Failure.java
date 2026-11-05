package com.lltool.core.common.result;

import com.lltool.core.common.error.AppError;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public record Failure<T>(AppError error) implements Result<T> {

    public Failure {
        Objects.requireNonNull(error, "error cannot be null");
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    @Override
    public T getDataOrThrow() {
        throw new NoSuchElementException("Cannot call getDataOrThrow() on a Failure Result: " + error.code() + " - " + error.message());
    }

    @Override
    public Optional<T> getData() {
        return Optional.empty();
    }

    @Override
    public Optional<AppError> getError() {
        return Optional.of(error);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        return (Result<R>) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        return (Result<R>) this;
    }

    @Override
    public Result<T> onSuccess(Consumer<? super T> action) {
        // No-op for Failure
        return this;
    }

    @Override
    public Result<T> onFailure(Consumer<? super AppError> action) {
        action.accept(error);
        return this;
    }
}

