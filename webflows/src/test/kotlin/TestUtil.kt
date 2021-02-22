import com.schibsted.account.android.webflows.util.ResultOrError
import org.junit.Assert
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

fun await(timeoutSeconds: Long = 1, func: (() -> Unit) -> Unit) {
    val latch = CountDownLatch(1)

    func {
        latch.countDown()
    }

    Assert.assertTrue(latch.await(timeoutSeconds, TimeUnit.SECONDS))
}

fun <S, E> ResultOrError<S, E>.assertSuccess(func: (S) -> Unit) {
    Assert.assertTrue("$this is not a Success", this is ResultOrError.Success)
    func((this as ResultOrError.Success).value)
}

fun <S, E> ResultOrError<S, E>.assertError(func: (E) -> Unit) {
    Assert.assertTrue("$this is not an Error", this is ResultOrError.Failure)
    func((this as ResultOrError.Failure).error)
}
