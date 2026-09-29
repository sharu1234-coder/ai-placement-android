package com.aiplacementos.app.data.local

import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.database.AlarmDatabase

/**
 * Removes everything the previous account left on this device.
 *
 * The Stage 5 audit found logout cleared the auth tokens and nothing else. Every table in
 * [AlarmDatabase] is account-specific -- notifications carry the student's own titles and
 * bodies, alarm configs carry their free-text notes, challenges carry their answers -- and
 * none of them has a user column, because the database was designed when "this device" and
 * "this account" were the same thing. So student B, logging in on a shared or handed-on phone,
 * inherited student A's notification history and A's alarms, which AlarmManager would then
 * fire at them. Deleting the account did not help either: the server erased everything and the
 * device kept its copy.
 *
 * Purging is the whole fix rather than adding a user column to each table, for two reasons.
 * Existing rows have no recoverable owner -- there is no way to decide whose they are, and the
 * only honest thing to do with data of unknown ownership is delete it, which is what a
 * migration would have had to do anyway. And scoping by id leaves the rows on disk, so it
 * relies on every future query remembering its WHERE clause, whereas an empty table is safe no
 * matter what a later query forgets.
 *
 * The cost, stated plainly: a student who logs out loses their own local alarms and
 * notification history, and has to set the alarms up again. That is a real regression in
 * convenience and it is the deliberate trade -- these are device-local conveniences, logout is
 * an explicit act, and the alternative is a privacy hole that only shows up on someone else's
 * screen.
 *
 * Scheduled alarms are cancelled before the rows go, because cancelling needs the ids that are
 * about to be deleted. Doing it the other way round would delete the rows and leave the
 * pending intents armed, which is the exact failure this class exists to prevent -- the
 * database would look clean while the previous account's alarm still went off.
 */
class LocalAccountDataCleaner(
    private val database: AlarmDatabase,
    private val alarmScheduler: AlarmScheduler,
    private val pendingOperations: PendingOperationStore,
) {

    /**
     * Cancel every scheduled alarm and empty every account-specific table.
     *
     * Best-effort per alarm: one cancellation failing must not leave the remaining alarms
     * armed or the rows in place, so each is wrapped rather than allowed to abort the purge.
     * The deletes themselves are not wrapped -- if the database is unusable the caller needs
     * to know, because silently "succeeding" at a purge that did nothing is worse than failing.
     */
    suspend fun purge() {
        val alarmConfigDao = database.alarmConfigDao()

        alarmConfigDao.getAll().forEach { alarm ->
            runCatching { alarmScheduler.cancel(alarm.id) }
        }

        database.challengeDao().deleteAllQuestions()
        database.challengeDao().deleteAllChallenges()
        alarmConfigDao.deleteAll()
        database.notificationDao().deleteAll()
        // A generation still finishing belongs to this account too: left behind, the next
        // account would be shown it as "still finishing" and checked against its own path.
        pendingOperations.clearAll()
    }
}
