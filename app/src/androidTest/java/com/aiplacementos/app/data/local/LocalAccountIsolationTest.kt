package com.aiplacementos.app.data.local

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiplacementos.app.alarm.AlarmScheduler
import com.aiplacementos.app.alarm.BootRescheduleReceiver
import com.aiplacementos.app.database.AlarmDatabase
import com.aiplacementos.app.database.entity.AlarmConfigEntity
import com.aiplacementos.app.database.entity.ChallengeEntity
import com.aiplacementos.app.database.entity.ChallengeQuestionEntity
import com.aiplacementos.app.database.entity.NotificationEntity
import com.aiplacementos.app.network.TokenStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-03: what one account leaves behind on the device for the next one.
 *
 * Instrumented rather than unit tests on purpose. The defect is not in a branch that a fake
 * could stand in for -- it is in whether real Room tables are actually empty afterwards, and
 * whether a real BroadcastReceiver re-arms real alarms when nobody is signed in. A JVM test
 * with a fake database would have passed against the broken code, because the broken code
 * called every method it was supposed to except the ones that did not exist yet.
 */
@RunWith(AndroidJUnit4::class)
class LocalAccountIsolationTest {

    private lateinit var context: Context
    private lateinit var database: AlarmDatabase
    private lateinit var cleaner: LocalAccountDataCleaner

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // In-memory so one test cannot leak rows into the next, and so this never touches the
        // real placement_alarm.db on the device running the suite.
        database = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cleaner = LocalAccountDataCleaner(database, AlarmScheduler(context), PendingOperationStore.inMemory())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedPreviousAccountData(): Long {
        val alarmId = database.alarmConfigDao().insert(
            AlarmConfigEntity(
                hour = 7,
                minute = 30,
                repeatDays = "MON,TUE",
                enabled = true,
                title = "Revise SQL joins",
                notes = "Ask Priya about the interview",
            )
        )
        database.notificationDao().insert(
            NotificationEntity(
                title = "Your readiness rose to 62%",
                body = "SQL practice paid off",
                receivedAt = 1_700_000_000_000L,
            )
        )
        database.challengeDao().upsertChallenge(
            ChallengeEntity(
                clientAlarmId = "client-1",
                alarmConfigId = alarmId,
                status = "in_progress",
                createdAtEpochMillis = 1_700_000_000_000L,
            )
        )
        database.challengeDao().upsertQuestions(
            listOf(
                ChallengeQuestionEntity(
                    clientAlarmId = "client-1",
                    questionIndex = 0,
                    backendQuestionId = 11,
                    skillTag = "SQL",
                    prompt = "What does INNER JOIN return?",
                    optionsJson = "[]",
                    isCorrect = false,
                )
            )
        )
        return alarmId
    }

    private suspend fun localRowCounts(): List<Int> = listOf(
        database.alarmConfigDao().getAll().size,
        database.notificationDao().getUnreadCount(),
        database.challengeDao().getAllInProgress().size,
        database.challengeDao().getQuestions("client-1").size,
    )

    @Test
    fun purge_empties_every_account_specific_table() = runBlocking {
        seedPreviousAccountData()
        assertTrue("fixture did not seed", localRowCounts().all { it > 0 })

        cleaner.purge()

        assertEquals(listOf(0, 0, 0, 0), localRowCounts())
    }

    @Test
    fun purge_leaves_nothing_for_a_reboot_to_re_arm() = runBlocking {
        seedPreviousAccountData()

        cleaner.purge()

        // The boot receiver re-arms from getAllEnabled(). An empty result is what makes a
        // reboot after logout a no-op rather than a resurrection.
        assertEquals(0, database.alarmConfigDao().getAllEnabled().size)
    }

    @Test
    fun purge_is_safe_to_run_when_there_is_nothing_to_purge() = runBlocking {
        // Logout on a fresh install, and the login-time purge when the fingerprint is simply
        // absent, both hit this path -- it must not throw.
        cleaner.purge()

        assertEquals(listOf(0, 0, 0, 0), localRowCounts())
    }

    @Test
    fun purge_is_idempotent() = runBlocking {
        seedPreviousAccountData()

        cleaner.purge()
        cleaner.purge()

        assertEquals(listOf(0, 0, 0, 0), localRowCounts())
    }

    @Test
    fun boot_does_not_re_arm_alarms_when_nobody_is_signed_in() = runBlocking {
        // The real TokenStore on this device, deliberately: the receiver reads exactly this,
        // and the point of the test is that the guard consults a real session rather than a
        // value a fake handed it.
        TokenStore(context).clear()

        val armed = BootRescheduleReceiver.rescheduleEnabledAlarms(context)

        // Zero is the guarantee: a reboot after logout must not turn the previous account's
        // rows back into live alarms. The real device database is deliberately not seeded here
        // -- the guard has to return before reading it at all.
        assertEquals(0, armed)
    }

    @Test
    fun a_non_boot_intent_is_ignored_entirely() {
        // goAsync() returns null outside a real system dispatch, so reaching the coroutine at
        // all would NPE on a background thread. Returning early for the wrong action is what
        // keeps this safe, and this asserts it by the absence of that crash.
        BootRescheduleReceiver().onReceive(context, Intent(Intent.ACTION_TIME_TICK))
    }
}

/**
 * The account marker that decides whether the data already on a device belongs to whoever is
 * signing in. Pure function, but it lives here because TokenStore drags in DataStore and Tink.
 */
@RunWith(AndroidJUnit4::class)
class AccountFingerprintTest {

    @Test
    fun the_same_address_always_produces_the_same_marker() {
        assertEquals(
            TokenStore.fingerprintOf("priya@example.com"),
            TokenStore.fingerprintOf("priya@example.com"),
        )
    }

    @Test
    fun case_and_surrounding_whitespace_do_not_make_a_new_account() {
        // The server treats these as one student; so must the device, or a student who types
        // their address differently gets their own local data wiped for no reason.
        assertEquals(
            TokenStore.fingerprintOf("priya@example.com"),
            TokenStore.fingerprintOf("  Priya@Example.COM "),
        )
    }

    @Test
    fun two_students_produce_different_markers() {
        assertNotEquals(
            TokenStore.fingerprintOf("priya@example.com"),
            TokenStore.fingerprintOf("arjun@example.com"),
        )
    }

    @Test
    fun the_marker_does_not_contain_the_address() {
        val fingerprint = TokenStore.fingerprintOf("priya@example.com")

        assertTrue(fingerprint.none { it == '@' })
        assertEquals(64, fingerprint.length)
    }

    @Test
    fun the_stored_marker_survives_a_round_trip_and_is_cleared_by_logout() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = TokenStore(context)
        val fingerprint = TokenStore.fingerprintOf("roundtrip@example.com")

        store.setAccountFingerprint(fingerprint)
        assertEquals(fingerprint, store.accountFingerprint())

        store.clear()

        // Absent after logout, so the next login sees a mismatch and purges -- failing towards
        // deleting too much rather than too little.
        assertNull(store.accountFingerprint())
    }
}
