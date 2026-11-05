package com.lltool.core.common.result;

import com.lltool.core.common.error.AppError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    @DisplayName("Success result wraps data and allows functional transformation")
    void testSuccessResult() {
        Result<String> res = Result.success("Hello");

        assertTrue(res.isSuccess());
        assertFalse(res.isFailure());
        assertEquals("Hello", res.getDataOrThrow());
        assertTrue(res.getData().isPresent());
        assertTrue(res.getError().isEmpty());

        Result<Integer> mapped = res.map(String::length);
        assertTrue(mapped.isSuccess());
        assertEquals(5, mapped.getDataOrThrow());

        Result<String> flatMapped = res.flatMap(s -> Result.success(s + " World"));
        assertEquals("Hello World", flatMapped.getDataOrThrow());

        AtomicBoolean executed = new AtomicBoolean(false);
        res.onSuccess(data -> executed.set(true));
        assertTrue(executed.get());
    }

    @Test
    @DisplayName("Failure result wraps AppError and propagates without mapping")
    void testFailureResult() {
        AppError err = AppError.validation("VAL_001", "Invalid name");
        Result<String> res = Result.failure(err);

        assertFalse(res.isSuccess());
        assertTrue(res.isFailure());
        assertTrue(res.getData().isEmpty());
        assertTrue(res.getError().isPresent());
        assertEquals("VAL_001", res.getError().get().code());

        // Mapping on failure should return the same failure
        Result<Integer> mapped = res.map(String::length);
        assertTrue(mapped.isFailure());
        assertEquals("VAL_001", mapped.getError().get().code());

        assertEquals("fallback", res.orElse("fallback"));

        AtomicBoolean failureExecuted = new AtomicBoolean(false);
        res.onFailure(e -> failureExecuted.set(true));
        assertTrue(failureExecuted.get());
    }

    @Test
    @DisplayName("Java pattern matching on sealed Result interface")
    void testPatternMatching() {
        Result<Integer> res = Result.success(42);

        String desc = switch (res) {
            case Success<Integer> s -> "Value=" + s.data();
            case Failure<Integer> f -> "Error=" + f.error().code();
        };

        assertEquals("Value=42", desc);
    }
}

