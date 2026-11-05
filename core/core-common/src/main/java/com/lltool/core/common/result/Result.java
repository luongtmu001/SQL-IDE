package com.lltool.core.common.result;

import com.lltool.core.common.error.AppError;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Standardized functional Result monad for boundaries and use cases.
 * Represents either a Success containing data T or a Failure containing an AppError.
 */
public sealed interface Result<T> permits Success, Failure {

    boolean isSuccess();

    default boolean isFailure() {
        return !isSuccess();
    }

    T getDataOrThrow();

    Optional<T> getData();

    Optional<AppError> getError();

    <R> Result<R> map(Function<? super T, ? extends R> mapper);

    <R> Result<R> flatMap(Function<? super T, Result<R>> mapper);

    Result<T> onSuccess(Consumer<? super T> action);

    Result<T> onFailure(Consumer<? super AppError> action);

    default T orElse(T other) {
        return isSuccess() ? getDataOrThrow() : other;
    }

    default T orElseGet(Function<AppError, ? extends T> supplier) {
        return isSuccess() ? getDataOrThrow() : supplier.apply(getError().orElseThrow());
    }

    static <T> Result<T> success(T data) {
        return new Success<>(data);
    }

    static Result<Void> success() {
        return new Success<>(null);
    }

    static <T> Result<T> failure(AppError error) {
        Objects.requireNonNull(error, "error cannot be null");
        return new Failure<>(error);
    }
}

