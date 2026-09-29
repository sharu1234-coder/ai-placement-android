package com.aiplacementos.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// A damaged file is read as "nothing pending" rather than thrown: the worst that loses is one
// "still finishing" state, and the server still has the result for the screen to show.
private val Context.pendingOperationsDataStore by preferencesDataStore(
    name = "pending_operations",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

private val CAREER_PATH_ID = stringPreferencesKey("career_path_generation.operation_id")
private val CAREER_PATH_ROLE = stringPreferencesKey("career_path_generation.requested_role")
private val CAREER_PATH_STARTED_AT = longPreferencesKey("career_path_generation.started_at")
private val CAREER_PATH_BEFORE = stringPreferencesKey("career_path_generation.before")
private val CAREER_PATH_BEFORE_ID = intPreferencesKey("career_path_generation.before_path_id")
private val CAREER_PATH_BEFORE_UPDATED_AT = stringPreferencesKey("career_path_generation.before_updated_at")

/**
 * Long requests this device has sent and not yet seen the end of -- see
 * [PendingCareerPathGeneration] for why they are remembered at all.
 *
 * Its own file in the persistence TokenStore already uses, rather than a new database or a
 * table in AlarmDatabase: this is a few scalar fields that must outlive the process, nothing
 * more. Not encrypted, because nothing in it is a secret. It is account-specific, though, so
 * [LocalAccountDataCleaner] empties it with everything else the previous account left behind.
 */
class PendingOperationStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.pendingOperationsDataStore)

    /** Null when nothing is pending -- and when the file cannot be read, for the same reason a
     * damaged one is treated as empty. */
    suspend fun careerPathGeneration(): PendingCareerPathGeneration? = try {
        dataStore.data.first().careerPathGeneration()
    } catch (e: IOException) {
        null
    }

    suspend fun saveCareerPathGeneration(pending: PendingCareerPathGeneration) {
        dataStore.edit { prefs ->
            prefs[CAREER_PATH_ID] = pending.operationId
            prefs[CAREER_PATH_ROLE] = pending.requestedRole
            prefs[CAREER_PATH_STARTED_AT] = pending.startedAtMillis
            prefs.remove(CAREER_PATH_BEFORE_ID)
            prefs.remove(CAREER_PATH_BEFORE_UPDATED_AT)
            when (val before = pending.before) {
                CurrentPathBefore.None -> prefs[CAREER_PATH_BEFORE] = BEFORE_NONE
                CurrentPathBefore.Unknown -> prefs[CAREER_PATH_BEFORE] = BEFORE_UNKNOWN
                is CurrentPathBefore.Path -> {
                    prefs[CAREER_PATH_BEFORE] = BEFORE_PATH
                    prefs[CAREER_PATH_BEFORE_ID] = before.id
                    prefs[CAREER_PATH_BEFORE_UPDATED_AT] = before.updatedAt
                }
            }
        }
    }

    /** Only while the stored record is still [operationId]: an attempt that ends late must never
     * erase the record of a newer one. */
    suspend fun clearCareerPathGeneration(operationId: String) {
        dataStore.edit { prefs ->
            if (prefs[CAREER_PATH_ID] == operationId) prefs.removeCareerPathGeneration()
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private const val BEFORE_NONE = "none"
        private const val BEFORE_PATH = "path"
        private const val BEFORE_UNKNOWN = "unknown"

        /**
         * The same store over memory instead of a file, for a view model constructed without the
         * app's -- which is how the Phase 9-11 view-model tests build one. It forgets on process
         * death, so production must never use it: NetworkModule owns the file-backed instance.
         */
        fun inMemory(): PendingOperationStore = PendingOperationStore(InMemoryPreferences())

        private fun Preferences.careerPathGeneration(): PendingCareerPathGeneration? {
            val id = this[CAREER_PATH_ID] ?: return null
            val role = this[CAREER_PATH_ROLE] ?: return null
            val startedAt = this[CAREER_PATH_STARTED_AT] ?: return null
            val beforeId = this[CAREER_PATH_BEFORE_ID]
            val beforeUpdatedAt = this[CAREER_PATH_BEFORE_UPDATED_AT]
            val before = when (this[CAREER_PATH_BEFORE]) {
                BEFORE_NONE -> CurrentPathBefore.None
                BEFORE_PATH ->
                    if (beforeId != null && beforeUpdatedAt != null) {
                        CurrentPathBefore.Path(beforeId, beforeUpdatedAt)
                    } else {
                        CurrentPathBefore.Unknown
                    }
                else -> CurrentPathBefore.Unknown
            }
            return PendingCareerPathGeneration(id, role, startedAt, before)
        }

        private fun MutablePreferences.removeCareerPathGeneration() {
            remove(CAREER_PATH_ID)
            remove(CAREER_PATH_ROLE)
            remove(CAREER_PATH_STARTED_AT)
            remove(CAREER_PATH_BEFORE)
            remove(CAREER_PATH_BEFORE_ID)
            remove(CAREER_PATH_BEFORE_UPDATED_AT)
        }
    }
}

/** DataStore's contract -- serialised updates, the new value published as it commits -- over a
 * field instead of a file. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val lock = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        lock.withLock { transform(state.value).also { state.value = it } }
}
