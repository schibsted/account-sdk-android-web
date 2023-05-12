package com.schibsted.account.webflows.storage

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.GsonBuilder
import com.schibsted.account.testutil.Fixtures
import com.schibsted.account.testutil.assertRight
import com.schibsted.account.webflows.persistence.EncryptedSharedPrefsStorage
import com.schibsted.account.webflows.persistence.MigratingSessionStorage
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
    private val gson = GsonBuilder().setDateFormat("MM dd, yyyy HH:mm:ss").create()
    private val userSession =
        StoredUserSession(Fixtures.clientConfig.clientId, Fixtures.userTokens, Date())

    @Test
    fun testMigratingStorageReadsEncryptedStorage() {
        val encryptedStorage: EncryptedSharedPrefsStorage = mockk()
        val mockContext = getMockContext(emptyStorage = true)

        val migratingStorage = MigratingSessionStorage(mockContext, encryptedStorage)
        every {
            encryptedStorage.get(any(), any())
        } answers {
            val callback = secondArg<StorageReadCallback>()
            callback(Either.Right(userSession))
        }

        migratingStorage.get(Fixtures.clientConfig.clientId) {
            it.assertRight { storedUserSession ->
                assertEquals(userSession.userTokens.idToken, storedUserSession?.userTokens?.idToken)
            }
        }
    }

    @Test
    fun testMigratingStorageReadsFromNewStorage() {
        val encryptedStorage: EncryptedSharedPrefsStorage = mockk()
        val mockContext = getMockContext(emptyStorage = false)
        val migratingStorage = MigratingSessionStorage(mockContext, encryptedStorage)

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

    private fun getMockEditor(): SharedPreferences.Editor {
        val mockEditor = mockk<SharedPreferences.Editor>()
        every {
            mockEditor.putString(any(), any())
        } returns mockEditor
        every {
            mockEditor.apply()
        } returns Unit
        return mockEditor
    }

    private fun getMockContext(emptyStorage: Boolean): Context {
        val mockContext: Context = mockk()
        every {
            mockContext.getSharedPreferences(any(), any())
        } returns mockk {
            every {
                edit()
            } returns getMockEditor()
            every {
                getString(
                    Fixtures.clientConfig.clientId,
                    any()
                )
            } returns if (emptyStorage) null else gson.toJson(userSession)
        }
        return mockContext
    }
}
