package com.lltool.core.common.result;

import com.lltool.core.common.error.AppError;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public record Success<T>(T data) implements Result<T> {

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public T getDataOrThrow() {
        return data;
    }

    @Override
    public Optional<T> getData() {
        return Optional.ofNullable(data);
    }

    @Override
    public Optional<AppError> getError() {
        return Optional.empty();
    }

    @Override
    public <R> Result<R> map(Function<? super T, ? extends R> mapper) {
        try {
            return new Success<>(mapper.apply(data));
        } catch (Exception e) {
            return new Failure<>(AppError.system("Mapping failed: " + e.getMessage(), e));
        }
    }

    @Override
    public <R> Result<R> flatMap(Function<? super T, Result<R>> mapper) {
        try {
            return mapper.apply(data);
        } catch (Exception e) {
            return new Failure<>(AppError.system("FlatMap failed: " + e.getMessage(), e));
        }
    }

    @Override
    public Result<T> onSuccess(Consumer<? super T> action) {
        action.accept(data);
        return this;
    }

    @Override
    public Result<T> onFailure(Consumer<? super AppError> action) {
        // No-op for Success
        return this;
    }
}

