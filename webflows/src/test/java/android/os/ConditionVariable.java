// CHECKSTYLE:OFF

package android.os;

/**
 * Class that implements the condition variable locking paradigm.
 * <p>
 * <p>
 * This differs from the built-in java.lang.Object wait() and notify()
 * in that this class contains the condition to wait on itself.  That means
 * open(), close() and block() are sticky.  If open() is called before block(),
 * block() will not block, and instead return immediately.
 * <p>
 * <p>
 * This class uses itself as the object to wait on, so if you wait()
 * or notify() on a ConditionVariable, the results are undefined.
 */
public class ConditionVariable {
    private volatile boolean mCondition;

    /**
     * Create the ConditionVariable in the default closed state.
     */
    public ConditionVariable() {
        mCondition = false;
    }

    /**
     * Create the ConditionVariable with the given state.
     * <p>
     * <p>
     * Pass true for opened and false for closed.
     */
    public ConditionVariable(boolean state) {
        mCondition = state;
    }

    /**
     * Open the condition, and release all threads that are blocked.
     * <p>
     * <p>
     * Any threads that later approach block() will not block unless close()
     * is called.
     */
    public void open() {
        synchronized (this) {
            boolean old = mCondition;
            mCondition = true;
            if (!old) {
                this.notifyAll();
            }
        }
    }

    /**
     * Reset the condition to the closed state.
     * <p>
     * <p>
     * Any threads that call block() will block until someone calls open.
     */
    public void close() {
        synchronized (this) {
            mCondition = false;
        }
    }

    /**
     * Block the current thread until the condition is opened.
     * <p>
     * <p>
     * If the condition is already opened, return immediately.
     */
    public void block() {
        synchronized (this) {
            while (!mCondition) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }

    /**
     * Block the current thread until the condition is opened or until
     * timeout milliseconds have passed.
     * <p>
     * <p>
     * If the condition is already opened, return immediately.
     *
     * @param timeout the maximum time to wait in milliseconds.
     * @return true if the condition was opened, false if the call returns
     * because of the timeout.
     */
    public boolean block(long timeout) {
        // Object.wait(0) means wait forever, to mimic this, we just
        // call the other block() method in that case.  It simplifies
        // this code for the common case.
        if (timeout != 0) {
            synchronized (this) {
                long now = System.currentTimeMillis();
                long end = now + timeout;
                while (!mCondition && now < end) {
                    try {
                        this.wait(end - now);
                    } catch (InterruptedException e) {
                    }
                    now = System.currentTimeMillis();
                }
                return mCondition;
            }
        } else {
            this.block();
            return true;
        }
    }
}
