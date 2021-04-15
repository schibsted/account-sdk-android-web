package com.schibsted.account.webflows.util

import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private class TestOperation<T>(private val results: List<T>, private val taskDelay: Long = 20) {
    private var currentResultIndex = AtomicInteger(0)

    fun doWork(): T {
        Thread.sleep(taskDelay) // artificial delay to simulate slow running task
        val index = currentResultIndex.getAndAdd(1);
        if (index >= results.size) {
            return results.last()
        }
        return results[index]
    }
}

class BestEffortRunOnceTaskTest {
    private fun <T> runInParallel(numThreads: Int, task: BestEffortRunOnceTask<T>): Map<Long, T?> {
        val results = ConcurrentHashMap<Long, T?>()

        val threads = (0 until numThreads).map { i ->
            thread {
                results[Thread.currentThread().id] = runBlocking { task.run() }
            }
        }

        for (t in threads) {
            t.join()
        }

        return results
    }

    @Test
    fun runOnlyExecutesOperationOnce() {
        val opMock = spyk(TestOperation(listOf("First result", "Second result")))

        val results = runInParallel(3, BestEffortRunOnceTask {
            opMock.doWork()
        })

        verify(exactly = 1) { opMock.doWork() }
        // all three threads should get the same result
        assertEquals(
            listOf("First result", "First result", "First result"),
            results.values.toList()
        )
    }

    @Test
    fun runRepeatsOperationIfLockTimesOut() {
        val results = listOf(
            "First result",
            "Second result",
            "Third result"
        )
        val opMock = spyk(TestOperation(results, 30))

        val actualResults = runInParallel(3, BestEffortRunOnceTask(10) {
            opMock.doWork()
        })

        verify(exactly = 3) { opMock.doWork() }
        // all three threads should repeat the operation, getting different results
        assertEquals(results.sorted(), actualResults.values.map { v -> v!! }.sorted())
    }
}
