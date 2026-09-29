package com.aiplacementos.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The real store over a real preferences file -- the same DataStore implementation production
 * uses, pointed at a temporary file instead of the app's files directory.
 */
class PendingOperationStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val dataStores = mutableListOf<DataStore<Preferences>>()

    /** A store over the one file this test uses; calling it again after [kill] is a new process. */
    private fun store(): PendingOperationStore {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { scopes += it }
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.root, "pending_operations.preferences_pb")
        }
        dataStores += dataStore
        return PendingOperationStore(dataStore)
    }

    /** Process death, as far as the file is concerned: every open instance goes away. */
    private fun kill() = runBlocking {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        scopes.clear()
    }

    @After
    fun tearDown() = kill()

    private val record = PendingCareerPathGeneration(
        operationId = "op-1",
        requestedRole = "Data Scientist",
        startedAtMillis = 1_790_000_000_000L,
        before = CurrentPathBefore.Path(id = 4, updatedAt = "2026-09-26T15:38:32.648043"),
    )

    @Test
    fun `nothing is pending until something is saved`() = runBlocking {
        assertNull(store().careerPathGeneration())
    }

    @Test
    fun `every prior-state shape reads back exactly`() = runBlocking {
        val store = store()
        for (before in listOf(CurrentPathBefore.None, CurrentPathBefore.Unknown, record.before)) {
            store.saveCareerPathGeneration(record.copy(before = before))
            assertEquals(record.copy(before = before), store.careerPathGeneration())
        }
    }

    @Test
    fun `a record survives process death`() = runBlocking {
        store().saveCareerPathGeneration(record)

        kill()

        assertEquals("a new process reads the same record back", record, store().careerPathGeneration())
    }

    @Test
    fun `an older attempt finishing late does not clear a newer one`() = runBlocking {
        val store = store()
        store.saveCareerPathGeneration(record.copy(operationId = "op-2"))

        store.clearCareerPathGeneration("op-1")

        assertEquals("op-2", store.careerPathGeneration()?.operationId)
    }

    @Test
    fun `its own attempt clears it`() = runBlocking {
        val store = store()
        store.saveCareerPathGeneration(record)

        store.clearCareerPathGeneration("op-1")

        assertNull(store.careerPathGeneration())
        kill()
        assertNull("and it stays cleared across a restart", store().careerPathGeneration())
    }

    @Test
    fun `clearAll leaves nothing behind`() = runBlocking {
        val store = store()
        store.saveCareerPathGeneration(record)

        store.clearAll()

        assertNull(store.careerPathGeneration())
        assertEquals(emptySet<String>(), dataStores.last().data.first().asMap().keys.map { it.name }.toSet())
    }

    @Test
    fun `only the documented operation metadata is ever written`() = runBlocking {
        store().saveCareerPathGeneration(record)

        // No token, resume, prompt or response body can be in a file whose keys are exactly these.
        assertEquals(
            setOf(
                "career_path_generation.operation_id",
                "career_path_generation.requested_role",
                "career_path_generation.started_at",
                "career_path_generation.before",
                "career_path_generation.before_path_id",
                "career_path_generation.before_updated_at",
            ),
            dataStores.last().data.first().asMap().keys.map { it.name }.toSet(),
        )
    }

    @Test
    fun `an unreadable file reads as nothing pending instead of throwing`() = runBlocking {
        val unreadable = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("disk") }
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                throw IOException("disk")
        }

        assertNull(PendingOperationStore(unreadable).careerPathGeneration())
    }

    @Test
    fun `the in-memory store keeps the same contract`() = runBlocking {
        val store = PendingOperationStore.inMemory()
        store.saveCareerPathGeneration(record)
        store.clearCareerPathGeneration("someone-else")
        assertEquals(record, store.careerPathGeneration())
        store.clearCareerPathGeneration("op-1")
        assertNull(store.careerPathGeneration())
    }
}
