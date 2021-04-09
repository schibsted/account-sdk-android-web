package com.schibsted.account.android.webflows.util

import com.schibsted.account.android.webflows.util.Either.*
import org.junit.Assert.*
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
    fun foreachShouldApplyToRightValue() {
        var called = false
        val value = Right(1)
        assertEquals(value, value.foreach { called = true })
        assertTrue(called)
    }

    @Test
    fun foreachShouldNotApplyToLeftValue() {
        var called = false
        val value = Left(1)
        assertEquals(value, value.foreach { called = true })
        assertFalse(called)
    }

    class LeftProjectionTest {
        @Test
        fun mapShouldTransformLeftValue() {
            val projection = Left(1).left()
            assertEquals(Left(2), projection.map { it + 1 })
        }

        @Test
        fun mapShouldNotTransformRightValue() {
            val projection: LeftProjection<Int, Int> = Right(1).left()
            assertEquals(Right(1), projection.map { it + 1 })
        }

        @Test
        fun foreachShouldApplyToLeftValue() {
            var called = false
            val value = Left(1).left()
            assertEquals(value, value.foreach { called = true })
            assertTrue(called)
        }

        @Test
        fun foreachShouldNotApplyToRightValue() {
            var called = false
            val value = Right(1).left()
            assertEquals(value, value.foreach { called = true })
            assertFalse(called)
        }
    }
}
