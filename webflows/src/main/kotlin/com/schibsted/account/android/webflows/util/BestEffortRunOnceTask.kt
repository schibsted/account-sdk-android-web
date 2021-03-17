package com.schibsted.account.android.webflows.util

import android.os.ConditionVariable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Task implementation that tries to run the given operation only once when called concurrently.
 *
 * How it works:
 *  1. A thread calling `run` will first check if the operation is already in progress.
 *  2. If it is not, the thread will mark the task as in progress and grab a lock before
 *     running the operation.
 *  3. A subsequent thread calling `run` will see the operation is already in progress and then
 *     block while waiting for the lock to be released.
 *  4. If waiting for the lock times out, the thread will repeat the operation to get a result.
 */
internal class BestEffortRunOnceTask<T>(
    private val timeoutMilliSeconds: Long = 1000,
    private val block: () -> T
) {
    private val lock = ConditionVariable()
    private val inProgress = AtomicBoolean(false)

    @Volatile
    private var value: T? = null

    fun run(): T? {
        return when {
            inProgress.compareAndSet(false, true) -> {
                // only the first thread will run this
                lock.close()
                value = block()
                inProgress.set(false)
                lock.open()

                value
            }
            lock.block(timeoutMilliSeconds) -> {
                // all subsequent threads will wait instead
                value
            }
            else -> {
                // the previous check will return false on timeout, possibly repeating the operation
                block()
            }
        }
    }
}
