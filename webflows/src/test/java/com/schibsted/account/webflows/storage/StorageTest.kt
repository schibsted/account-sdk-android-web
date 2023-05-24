package com.schibsted.account.webflows.storage

import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.MigratingSessionStorage
import com.schibsted.account.webflows.persistence.SharedPrefsStorage
import com.schibsted.account.webflows.persistence.StorageReadCallback
import com.schibsted.account.webflows.user.StoredUserSession
import com.schibsted.account.webflows.util.Either
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.Assert.assertEquals
import org.junit.Test
import java.util.*

class MigratingStorageTest {
    private val userSession =
        StoredUserSession(Fixtures.clientConfig.clientId, Fixtures.userTokens, Date())

    @Test
    fun testMigratingStorageReadsEncryptedStorage() {
        val encryptedStorage: EncryptedSharedPrefsStorage = mockk {
            every {
                get(any(), any())
            } answers {
                val callback = secondArg<StorageReadCallback>()
                callback(Either.Right(userSession))
            }
        }
        val sharedPrefsStorage: SharedPrefsStorage = mockk(relaxed = true)
        val migratingStorage = MigratingSessionStorage(sharedPrefsStorage, encryptedStorage)


        migratingStorage.get(Fixtures.clientConfig.clientId) {
            it.assertRight { storedUserSession ->
                assertEquals(userSession.userTokens.idToken, storedUserSession?.userTokens?.idToken)
            }
        }
    }

    @Test
    fun testMigratingStorageReadsFromNewStorage() {
        val encryptedStorage: EncryptedSharedPrefsStorage = mockk()
        val sharedPrefsStorage: SharedPrefsStorage = mockk(relaxed = true)
        val migratingStorage = MigratingSessionStorage(sharedPrefsStorage, encryptedStorage)

        migratingStorage.get(Fixtures.clientConfig.clientId) {
            it.assertRight { storedUserSession ->
                assertEquals(
                    userSession.userTokens.idToken,
                    storedUserSession?.userTokens?.idToken
                )
            }
            verify(exactly = 0) {
                encryptedStorage.get(any(), any())
            }
        }
    }
}
