package com.schibsted.account.webflows.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test
import kotlin.concurrent.thread

private interface TestOperation<T> {
    fun doWork(): T
}

class BestEffortRunOnceTaskTest {
    private fun <T> runInParallel(numThreads: Int, task: BestEffortRunOnceTask<T>): Map<Int, T?> {
        val results = mutableMapOf<Int, T?>()

        val threads = (0 until numThreads).map { i ->
            thread {
                results[i] = task.run()
            }
        }

        for (t in threads) {
            t.join()
        }

        return results
    }

    @Test
    fun runOnlyExecutesOperationOnce() {
        val opMock = mockk<TestOperation<String>> {
            every { doWork() } returnsMany listOf("First result", "Second result")
        }

        val results = runInParallel(3, BestEffortRunOnceTask {
            Thread.sleep(20) // artificial delay to force subsequent threads to wait for the first one
            opMock.doWork()
        })

        verify(exactly = 1) { opMock.doWork() }
        // All three threads got the same result
        assertEquals(
            results.values.toList(),
            listOf("First result", "First result", "First result")
        )
    }

    @Test
    fun runDoesNotRepeatOperationIfLockTimesOut() {
        val results = listOf(
            "First result",
            "Second result",
            "Third result"
        )
        val opMock = mockk<TestOperation<String>> {
            every { doWork() } returnsMany results
        }
        val actualResults = runInParallel(3, BestEffortRunOnceTask(10) {
            Thread.sleep(20)
            opMock.doWork()
        })

        verify(exactly = 1) { opMock.doWork() }
        assertEquals(results[0], actualResults[0]) // first thread should always get first result
        // order of second and third thread isn't guaranteed
        assertNull(actualResults[1])
        assertNull(actualResults[2])
    }
}
