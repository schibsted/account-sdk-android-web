package com.schibsted.account.webflows.util

import com.schibsted.account.webflows.util.Either.Left
import com.schibsted.account.webflows.util.Either.Right
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EitherTest {
    @Test
    fun mapShouldTransformRightValue() {
        val value = Right(1).map { it + 1 }
        assertEquals(Right(2), value)
    }

    @Test
    fun mapShouldNotTransformLeftValue() {
        val value: Either<Int, Int> = Left(1)
        assertEquals(Left(1), value.map { it + 1 })
    }

    @Test
    fun onSuccessShouldApplyToRightValue() {
        var called = false
        val value = Right(1)
        assertEquals(value, value.onSuccess { called = true })
        assertTrue(called)
    }

    @Test
    fun onSuccessShouldNotApplyToLeftValue() {
        var called = false
        val value = Left(1)
        assertEquals(value, value.onSuccess { called = true })
        assertFalse(called)
    }

    @Test
    fun onFailureShouldApplyToLeftValue() {
        var called = false
        val value = Left(1)
        assertEquals(value, value.onFailure { called = true })
        assertTrue(called)
    }

    @Test
    fun onFailureShouldNotApplyToRightValue() {
        var called = false
        val value = Right(1)
        assertEquals(value, value.onFailure { called = true })
        assertFalse(called)
    }
}
