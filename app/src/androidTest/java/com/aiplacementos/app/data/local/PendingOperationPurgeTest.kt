package com.aiplacementos.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.database.AlarmDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Logout and signing in as someone else both run [LocalAccountDataCleaner.purge]. A Career Path
 * generation still finishing belongs to the account that sent it: left behind, the next account
 * would be shown "still finishing" for it, and its checks would read the new account's path.
 */
@RunWith(AndroidJUnit4::class)
class PendingOperationPurgeTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val store = PendingOperationStore(
        PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "purge-${UUID.randomUUID()}.preferences_pb")
        },
    )

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    @Test
    fun purgeRemovesAGenerationStillFinishing() = runBlocking {
        val pending = PendingCareerPathGeneration("op-1", "Data Scientist", 1_790_000_000_000L, CurrentPathBefore.None)
        store.saveCareerPathGeneration(pending)
        assertEquals(pending, store.careerPathGeneration())

        LocalAccountDataCleaner(database, AlarmScheduler(context), store).purge()

        assertNull(store.careerPathGeneration())
    }
}
