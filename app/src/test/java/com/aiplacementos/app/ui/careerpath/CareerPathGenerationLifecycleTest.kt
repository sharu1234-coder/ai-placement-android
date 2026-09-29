package com.aiplacementos.app.ui.careerpath

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aiplacementos.app.data.local.CurrentPathBefore
import com.aiplacementos.app.data.local.PendingCareerPathGeneration
import com.aiplacementos.app.data.local.PendingOperationStore
import com.aiplacementos.app.data.remote.CareerPathApi
import com.aiplacementos.app.data.remote.addApiConverters
import com.aiplacementos.app.data.remote.clientNotResendingGenerations
import com.aiplacementos.app.data.repository.CareerPathRepositoryImpl
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import retrofit2.create

private typealias UiState = CareerPathRoleSelectionUiState

/**
 * F12-02 below the screen, end to end: the real view model, [CareerPathGenerationRecovery], the
 * real [PendingOperationStore] over a real preferences file, [CareerPathRepositoryImpl], Retrofit
 * and the production converter chain, against MockWebServer.
 *
 * The server side mirrors select_role(): nothing is visible on /current until the request
 * commits, and it commits whether or not the client is still there to hear about it. Losing the
 * response is simulated where Android does it -- after the request is on the server -- by an
 * interceptor that lets the server answer and then throws the SocketException the live
 * reproduction logged ("POST /career-path/generate failed after 14219ms: SocketException").
 *
 * View-model coroutines run on a single "fake-main" thread, as they do on Main in production;
 * the student's actions are posted to that thread too. Time never passes for real: the recovery
 * gets a fake clock, and its pause between checks advances it, so deadlines are deterministic.
 *
 * This is not proof of Android's background behaviour -- only a device shows that. It is proof
 * of what the app does with the failure Android produces.
 */
class CareerPathGenerationLifecycleTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
    private val main = mainExecutor.asCoroutineDispatcher()

    private lateinit var server: MockWebServer
    private val backend = Backend()
    private val lose = LoseGenerateResponse()
    private val refuse = RefuseGenerateConnection()

    @Volatile
    private var now = 1_790_000_000_000L
    private val dataStoreScopes = mutableListOf<CoroutineScope>()
    private val viewModelStores = mutableListOf<ViewModelStore>()
    private val collectors = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        server = MockWebServer().apply {
            dispatcher = backend
            start()
        }
    }

    @After
    fun tearDown() {
        lose.release()
        collectors.forEach { it.cancel() }
        killProcess()
        server.shutdown()
        Dispatchers.resetMain()
        main.close()
    }

    // ==========================================================================================
    // The server
    // ==========================================================================================

    /** select_role() as the client experiences it: /current changes only when a request commits. */
    private inner class Backend : Dispatcher() {
        val requests = CopyOnWriteArrayList<String>()
        private var current = "null"
        private var working: String? = null
        private var readsUntilCommit = 0

        /** What the server does when a generation request arrives. Default: build and answer. */
        @Volatile
        var onGenerate: (RecordedRequest) -> MockResponse = { MockResponse().setResponseCode(599) }

        @Synchronized
        fun setCurrent(json: String) {
            current = json
            working = null
        }

        /** The request is on the server, which commits [result] once /current has been read
         * [reads] more times -- "still working" as seen from outside. */
        @Synchronized
        fun commitAfterReads(reads: Int, result: String) {
            if (reads == 0) setCurrent(result) else {
                working = result
                readsUntilCommit = reads
            }
        }

        @Synchronized
        private fun readCurrent(): String {
            val result = working
            if (result != null) {
                if (readsUntilCommit == 0) setCurrent(result) else readsUntilCommit--
            }
            return current
        }

        override fun dispatch(request: RecordedRequest): MockResponse {
            val key = "${request.method} ${request.path}"
            requests += key
            return when (key) {
                "GET /career-path/role-categories" -> json("[\"Data & AI\"]")
                "GET /career-path/role-catalog" -> json(CATALOG)
                "GET /career-path/current" -> json(readCurrent())
                "POST /career-path/generate" -> onGenerate(request)
                else -> MockResponse().setResponseCode(599)
            }
        }

        val generatePosts get() = requests.count { it == "POST /career-path/generate" }
        val currentReads get() = requests.count { it == "GET /career-path/current" }
    }

    /** Android cutting the app's connection once the request is out: the server receives the
     * POST and does its work, and the client never sees the answer. [gate] holds the failure
     * back until the test has done whatever the student does meanwhile (usually: press Home). */
    private inner class LoseGenerateResponse : Interceptor {
        @Volatile
        var armed = false

        @Volatile
        var gate = CountDownLatch(0)

        fun holdUntilReleased() {
            gate = CountDownLatch(1)
        }

        fun release() = gate.countDown()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (!armed || request.method != "POST" || request.url.encodedPath != "/career-path/generate") {
                return chain.proceed(request)
            }
            chain.proceed(request).close()
            gate.await(10, TimeUnit.SECONDS)
            throw SocketException("Software caused connection abort")
        }
    }

    /** A connection that never opens: the request never reaches the server at all. */
    private inner class RefuseGenerateConnection : Interceptor {
        @Volatile
        var armed = false

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (armed && request.method == "POST" && request.url.encodedPath == "/career-path/generate") {
                throw ConnectException("Failed to connect to /10.0.2.2:8000")
            }
            return chain.proceed(request)
        }
    }

    // ==========================================================================================
    // The app: one "process" is a store over the preferences file plus the objects built on it
    // ==========================================================================================

    private inner class App(
        readTimeoutMillis: Long = 10_000,
        timing: GenerationRecoveryTiming = GenerationRecoveryTiming(),
        pause: (suspend (Long) -> Unit)? = null,
        /** The plain client, without NetworkModule's routing -- only for the control test below. */
        okHttpDefaults: Boolean = false,
    ) {
        val store: PendingOperationStore = run {
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { dataStoreScopes += it }
            PendingOperationStore(
                PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "pending_operations.preferences_pb") },
            )
        }
        private val client = OkHttpClient.Builder()
            .addInterceptor(lose)
            .addInterceptor(refuse)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        val repository = CareerPathRepositoryImpl(
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .let { if (okHttpDefaults) it.client(client) else it.clientNotResendingGenerations(client) }
                .addApiConverters()
                .build()
                .create<CareerPathApi>(),
        )
        val recovery = CareerPathGenerationRecovery(
            careerPathRepository = repository,
            store = store,
            timing = timing,
            clock = { now },
            pause = pause ?: { millis -> now += millis },
        )

        fun screen(): CareerPathRoleSelectionViewModel = onMain {
            val vmStore = ViewModelStore().also { viewModelStores += it }
            val owner = object : ViewModelStoreOwner {
                override val viewModelStore: ViewModelStore = vmStore
            }
            ViewModelProvider(owner, viewModelFactory { initializer { CareerPathRoleSelectionViewModel(repository, recovery) } })
                .get(CareerPathRoleSelectionViewModel::class.java)
        }

        fun record(): PendingCareerPathGeneration? = runBlocking { store.careerPathGeneration() }
    }

    /** Process death: every view model and every open store instance goes away, nothing else runs. */
    private fun killProcess() {
        onMain { viewModelStores.forEach { it.clear() } }
        viewModelStores.clear()
        runBlocking { dataStoreScopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
        dataStoreScopes.clear()
    }

    private fun <T> onMain(block: () -> T): T = runBlocking(main) { block() }

    private fun CareerPathRoleSelectionViewModel.visible() = onMain { onScreenResumed() }
    private fun CareerPathRoleSelectionViewModel.hidden() = onMain { onScreenPaused() }
    private fun CareerPathRoleSelectionViewModel.tap(role: String) = onMain { selectCustomRole(role) }

    private fun <T : Any> eventually(what: String, timeoutMillis: Long = 5_000, probe: () -> T?): T {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            probe()?.let { return it }
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting for $what")
            Thread.sleep(5)
        }
    }

    private inline fun <reified S : UiState> CareerPathRoleSelectionViewModel.awaitState(): S =
        eventually("state ${S::class.simpleName}, last seen ${uiState.value}") { uiState.value as? S }

    private fun CareerPathRoleSelectionViewModel.awaitCatalog(): CareerPathRoleSelectionUiState.Loaded =
        eventually("the catalog, last seen $uiState") { (uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { !it.isGenerating } }

    /** Every state the screen was put in, for asserting what the student never saw. */
    private fun CareerPathRoleSelectionViewModel.history(): List<UiState> {
        val seen = CopyOnWriteArrayList<UiState>()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also { collectors += it }
        scope.launch { uiState.collect { seen += it } }
        return seen
    }

    /** The student starts on the catalog, screen visible, and taps [role]; the request is on the
     * server when this returns, with its response being withheld. */
    private fun App.sendAndLoseResponse(role: String = "Data Scientist", then: CareerPathRoleSelectionViewModel.() -> Unit = {}): CareerPathRoleSelectionViewModel {
        lose.armed = true
        lose.holdUntilReleased()
        val screen = screen()
        screen.visible()
        screen.awaitCatalog()
        screen.tap(role)
        eventually("the generation request on the server") { backend.generatePosts.takeIf { it == 1 } }
        screen.then()
        lose.release()
        return screen
    }

    // ==========================================================================================
    // 1-2. The record's life around a request that answers
    // ==========================================================================================

    @Test
    fun `the generation is recorded before its request is sent`() {
        backend.setCurrent(path(1, "Data Analyst", "T1"))
        val app = App()
        val recordWhenTheServerGotIt = AtomicReference<PendingCareerPathGeneration?>(null)
        backend.onGenerate = {
            recordWhenTheServerGotIt.set(app.record())
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val screen = app.screen()
        screen.visible()
        screen.awaitCatalog()

        screen.tap("Data Scientist")
        eventually("the path to open") { screen.generatedPathId.value }

        val record = checkNotNull(recordWhenTheServerGotIt.get()) { "the record did not exist when the request arrived" }
        assertEquals("Data Scientist", record.requestedRole)
        assertEquals("the path that was current, as it stood", CurrentPathBefore.Path(1, "T1"), record.before)
        assertEquals(now, record.startedAtMillis)
    }

    @Test
    fun `a generation that answers clears its record and opens the path`() {
        backend.onGenerate = {
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val app = App()
        val screen = app.screen()
        screen.visible()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        assertEquals(2, eventually("the path to open") { screen.generatedPathId.value })
        assertNull("nothing is left to look for", app.record())
        assertEquals(1, backend.generatePosts)
    }

    // ==========================================================================================
    // 3. The lost response
    // ==========================================================================================

    @Test
    fun `a lost response is not a failure - the record stays and the screen says still finishing`() {
        backend.onGenerate = {
            backend.commitAfterReads(5, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val app = App()

        val screen = app.sendAndLoseResponse { hidden() } // the student pressed Home

        assertEquals(CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>())
        assertEquals("Data Scientist", app.record()?.requestedRole)
        assertEquals("only the pre-send read -- nothing is checked while the screen is hidden", 1, backend.currentReads)
    }

    @Test
    fun `a read timeout is not a failure either`() {
        backend.onGenerate = {
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)
        }
        val app = App(readTimeoutMillis = 500)
        val screen = app.screen()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        assertEquals(CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>())
        assertNotNull(app.record())
    }

    @Test
    fun `a connection dropped after the request arrived is not a failure either`() {
        backend.onGenerate = {
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        }
        val app = App()
        val screen = app.screen()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        assertNotNull(app.record())
        assertEquals("the server received the generation exactly once", 1, backend.generatePosts)
    }

    @Test
    fun `control - OkHttp's defaults would have sent that generation twice`() {
        // Why NetworkModule routes the generation through clientNotResendingGenerations: the same
        // drop, with the client OkHttp builds by default. The request was written on the pooled
        // connection the pre-send read had just used, so OkHttp retried it on a new one.
        backend.onGenerate = { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST) }
        val app = App(okHttpDefaults = true)
        val screen = app.screen()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        assertEquals(2, backend.generatePosts)
    }

    // ==========================================================================================
    // 4. Definite answers stay definite
    // ==========================================================================================

    @Test
    fun `a 500 is a real failure - the record is cleared and the next tap goes through`() {
        backend.onGenerate = { MockResponse().setResponseCode(500) }
        val app = App()
        val screen = app.screen()
        screen.visible()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        val failed = eventually("the failure") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.generationError != null } }
        assertEquals("Couldn't build your Career Path (500).", failed.generationError)
        assertNull(failed.unconfirmedMessage)
        assertNull("a definite answer leaves nothing to look for", app.record())

        backend.onGenerate = {
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        screen.tap("Data Scientist")
        eventually("the retry's path") { screen.generatedPathId.value }
        assertEquals(2, backend.generatePosts)
    }

    @Test
    fun `a 429 keeps its own message and clears the record`() {
        backend.onGenerate = { MockResponse().setResponseCode(429) }
        val app = App()
        val screen = app.screen()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        val failed = eventually("the failure") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.generationError != null } }
        assertEquals("You've reached the Career Path generation limit for now -- try again in a bit.", failed.generationError)
        assertNull(app.record())
    }

    @Test
    fun `a request that never left the phone is a real failure`() {
        refuse.armed = true
        val app = App()
        val screen = app.screen()
        screen.awaitCatalog()

        screen.tap("Data Scientist")

        val failed = eventually("the failure") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.generationError != null } }
        assertEquals("Couldn't reach the server. Check your connection.", failed.generationError)
        assertNull("nothing can be running on the server", app.record())
        assertEquals(0, backend.generatePosts)

        refuse.armed = false
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) }
        screen.tap("Data Scientist")
        eventually("the retry") { backend.generatePosts.takeIf { it == 1 } }
    }

    @Test
    fun `no connection before sending means nothing is sent or recorded`() {
        val app = App()
        val screen = app.screen()
        screen.awaitCatalog()
        server.shutdown() // the pre-send read of the current path cannot connect

        screen.tap("Data Scientist")

        val failed = eventually("the failure") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.generationError != null } }
        assertEquals("Couldn't reach the server. Check your connection.", failed.generationError)
        assertNull(app.record())
    }

    // ==========================================================================================
    // 5-6, 12. Process death, resume, cold start
    // ==========================================================================================

    @Test
    fun `a pending generation survives process death`() {
        backend.onGenerate = {
            backend.commitAfterReads(3, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val first = App()
        first.sendAndLoseResponse { hidden() }
        val before = eventually("the record") { first.record() }

        killProcess()

        val relaunched = App()
        assertEquals("the new process reads the same record", before, relaunched.record())
        val screen = relaunched.screen()
        assertEquals(CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>())
    }

    @Test
    fun `resuming looks at the current path and finds the result`() {
        backend.onGenerate = {
            backend.commitAfterReads(3, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val app = App()
        val screen = app.sendAndLoseResponse { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        assertEquals(1, backend.currentReads)

        screen.visible() // the student comes back

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
        assertEquals("the pre-send read, three that saw the old state, the one that found it", 5, backend.currentReads)
        assertNull(app.record())
        assertEquals(1, backend.generatePosts)
    }

    @Test
    fun `a cold start finds the result without sending the generation again`() {
        backend.onGenerate = {
            backend.commitAfterReads(2, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        App().sendAndLoseResponse { hidden() }
        killProcess()

        val relaunched = App()
        val screen = relaunched.screen()
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        screen.visible()

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
        assertEquals("the new process only reads", 1, backend.generatePosts)
        assertNull(relaunched.record())
    }

    @Test
    fun `closing the screen mid-request keeps the record for the next one`() {
        // Back during the building state: the view model is cleared and its request cancelled,
        // but the request may already be with the server.
        backend.onGenerate = {
            backend.commitAfterReads(1, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val app = App()
        app.sendAndLoseResponse {
            onMain { viewModelStores.forEach { it.clear() } }
        }

        assertEquals("Data Scientist", app.record()?.requestedRole)
        val reopened = app.screen()
        reopened.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        reopened.visible()
        assertEquals(2, eventually("the found path") { reopened.generatedPathId.value })
        assertEquals(1, backend.generatePosts)
    }

    // ==========================================================================================
    // 7-8. What counts as the result
    // ==========================================================================================

    @Test
    fun `the old path is not mistaken for the result`() {
        backend.setCurrent(path(1, "Data Analyst", "T1"))
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) } // commits nothing yet
        val app = App(pause = { ticks.receive() })
        val screen = app.sendAndLoseResponse { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()

        screen.visible()
        eventually("the first check") { backend.currentReads.takeIf { it == 2 } }
        assertEquals("Data Analyst is current, and it is not the result", CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), screen.uiState.value)
        assertNull(screen.generatedPathId.value)
        ticks.trySend(Unit)
        eventually("the second check") { backend.currentReads.takeIf { it == 3 } }
        assertNull(screen.generatedPathId.value)
        assertNotNull("still waiting", app.record())

        backend.commitAfterReads(0, path(2, "Data Scientist", "T2")) // the server finishes
        ticks.trySend(Unit)

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
    }

    @Test
    fun `re-selecting the current role waits for updated_at to move`() {
        backend.setCurrent(path(1, "Data Analyst", "T1"))
        backend.onGenerate = {
            backend.commitAfterReads(2, path(1, "Data Analyst", "T2"))
            created(path(1, "Data Analyst", "T2"))
        }
        val app = App()
        val screen = app.sendAndLoseResponse(role = "Data Analyst") { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()

        screen.visible()

        assertEquals(1, eventually("the found path") { screen.generatedPathId.value })
        assertEquals("two reads saw the untouched row, the third the committed one", 4, backend.currentReads)
    }

    // ==========================================================================================
    // 9. The deadline
    // ==========================================================================================

    @Test
    fun `past the deadline the outcome is reported as unconfirmed - never as a failure`() {
        backend.setCurrent(path(1, "Data Analyst", "T1"))
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) } // and never commits
        val app = App(timing = GenerationRecoveryTiming(deadlineMillis = 60_000, checkIntervalsMillis = listOf(5_000, 10_000)))
        val screen = app.sendAndLoseResponse { hidden() }
        val seen = screen.history()

        screen.visible()

        val after = eventually("the unconfirmed catalog") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.unconfirmedMessage != null } }
        assertEquals(
            "We couldn't confirm that your Data Scientist Career Path finished. You can choose it again below.",
            after.unconfirmedMessage,
        )
        assertNull(after.generationError)
        assertTrue("never presented as a failure", seen.none { it is CareerPathRoleSelectionUiState.Loaded && it.generationError != null })
        // Checks at 0, 5, 15, 25, 35, 45, 55 and a last one at 65s, past the 60s deadline.
        assertEquals("bounded: the pre-send read plus exactly eight checks", 9, backend.currentReads)
        assertNull("the attempt is over", app.record())

        backend.onGenerate = { created(path(3, "Data Scientist", "T3")) }
        screen.tap("Data Scientist")
        eventually("a new, explicit attempt") { backend.generatePosts.takeIf { it == 2 } }
    }

    @Test
    fun `the default schedule is bounded too`() {
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) } // never commits
        val app = App() // GenerationRecoveryTiming's defaults: 30 minutes, 5/10/15/30s
        val screen = app.sendAndLoseResponse { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()

        screen.visible()

        eventually("the unconfirmed catalog") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.unconfirmedMessage }
        // 0, 5, 15, 30s, then every 30s to 1800s: 4 + 59 checks, the last one at the deadline.
        assertEquals(1 + 63, backend.currentReads)
    }

    // ==========================================================================================
    // 10-11, 41. Duplicate protection
    // ==========================================================================================

    @Test
    fun `tapping while a generation is still finishing sends nothing`() {
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) } // never commits
        val app = App()
        val screen = app.sendAndLoseResponse { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()

        screen.tap("Data Scientist")
        screen.tap("Data Analyst")

        assertEquals(CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), screen.uiState.value)
        assertEquals(1, backend.generatePosts)
    }

    @Test
    fun `the view model refuses a second generation even when its own screen still offers the catalog`() {
        // Not the UI's guard: a second screen instance loaded its catalog before the first one's
        // request was recorded, so nothing on it is disabled. The stored record still refuses.
        backend.onGenerate = { created(path(2, "Data Scientist", "T2")) } // never commits
        val app = App()
        val other = app.screen()
        other.awaitCatalog()
        app.sendAndLoseResponse { hidden() }
        eventually("the record") { app.record() }

        other.tap("Data Scientist")

        assertEquals(CareerPathRoleSelectionUiState.StillFinishing("Data Scientist"), other.awaitState<CareerPathRoleSelectionUiState.StillFinishing>())
        assertEquals(1, backend.generatePosts)
    }

    @Test
    fun `rotation - the screen leaving and coming back mid-generation sends one request and still opens the result`() {
        // What a configuration change does to this view model: the composition is disposed (pause)
        // and a new one resumes it, while the request carries on. The Activity itself is recreated
        // in CareerPathGenerationRotationTest.
        val answer = CountDownLatch(1)
        backend.onGenerate = {
            answer.await(10, TimeUnit.SECONDS)
            backend.commitAfterReads(0, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val app = App()
        val screen = app.screen()
        screen.visible()
        screen.awaitCatalog()
        screen.tap("Data Scientist")
        eventually("the request on the server") { backend.generatePosts.takeIf { it == 1 } }

        screen.hidden()
        screen.visible()
        screen.tap("Data Scientist") // a tap landing during the recreation
        answer.countDown()

        assertEquals(2, eventually("the path to open") { screen.generatedPathId.value })
        assertEquals(1, backend.generatePosts)
    }

    @Test
    fun `rotation while still finishing restarts the checks without sending anything`() {
        backend.onGenerate = {
            backend.commitAfterReads(4, path(2, "Data Scientist", "T2"))
            created(path(2, "Data Scientist", "T2"))
        }
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val app = App(pause = { ticks.receive() })
        val screen = app.sendAndLoseResponse { hidden() }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        screen.visible()
        eventually("a first check") { backend.currentReads.takeIf { it == 2 } }

        screen.hidden()
        screen.visible() // recreated: looks again straight away
        eventually("an immediate check on the new composition") { backend.currentReads.takeIf { it == 3 } }
        repeat(3) { ticks.trySend(Unit) }

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
        assertEquals(1, backend.generatePosts)
    }

    @Test
    fun `the F12-03 chain - background, lost socket, return, retry tap - sends one generation`() {
        backend.onGenerate = {
            backend.commitAfterReads(2, path(2, "Data Analyst", "T2"))
            created(path(2, "Data Analyst", "T2"))
        }
        val app = App()
        lateinit var seen: List<UiState>
        val screen = app.sendAndLoseResponse(role = "Data Analyst") {
            seen = history()
            hidden() // Home, while the request is running
        }
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()

        screen.visible() // back in the app
        screen.tap("Data Analyst") // the retry the old screen used to invite

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
        assertEquals("exactly one generation request, ever", 1, backend.generatePosts)
        assertFalse(
            "and the student was never told it failed",
            seen.any { it is CareerPathRoleSelectionUiState.Loaded && it.generationError != null },
        )
    }

    // ==========================================================================================
    // 29-30. Stale records and "nothing pending"
    // ==========================================================================================

    private fun App.seed(startedAtMillis: Long, role: String = "Data Scientist", before: CurrentPathBefore = CurrentPathBefore.None) {
        runBlocking {
            store.saveCareerPathGeneration(PendingCareerPathGeneration("stale", role, startedAtMillis, before))
        }
    }

    @Test
    fun `a record older than its deadline gets one final check - found`() {
        backend.setCurrent(path(2, "Data Scientist", "T2"))
        val app = App()
        app.seed(startedAtMillis = now - 26 * 60 * 60_000L) // opened again the next day

        val screen = app.screen()
        screen.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        screen.visible()

        assertEquals(2, eventually("the found path") { screen.generatedPathId.value })
        assertEquals("one check, not a polling loop", 1, backend.currentReads)
        assertEquals(0, backend.generatePosts)
        assertNull(app.record())
    }

    @Test
    fun `a record older than its deadline gets one final check - not found - and never blocks the catalog`() {
        val app = App()
        app.seed(startedAtMillis = now - 26 * 60 * 60_000L)

        val screen = app.screen()
        screen.visible()

        val catalog = eventually("the catalog") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.takeIf { it.unconfirmedMessage != null } }
        assertFalse(catalog.isGenerating)
        assertEquals(1, backend.currentReads)
        assertNull(app.record())
        screen.tap("Data Scientist")
        eventually("a new attempt is possible") { backend.generatePosts.takeIf { it == 1 } }
    }

    @Test
    fun `a clock that moved backwards cannot hold the screen either`() {
        val app = App()
        app.seed(startedAtMillis = now + 60 * 60_000L) // "sent" an hour in the future

        val screen = app.screen()
        screen.visible()

        eventually("the catalog") { (screen.uiState.value as? CareerPathRoleSelectionUiState.Loaded)?.unconfirmedMessage }
        assertEquals(1, backend.currentReads)
    }

    @Test
    fun `with nothing pending the screen never looks`() {
        val app = App()
        val screen = app.screen()
        screen.visible()
        screen.awaitCatalog()
        screen.hidden()
        screen.visible()

        runBlocking { app.recovery.resolveAtLaunch() }

        assertEquals("no pending generation, no reconciliation", 0, backend.currentReads)
    }

    // ==========================================================================================
    // App launch: only an expired record is ended, and without a request
    // ==========================================================================================

    @Test
    fun `launch leaves a fresh record to the screen that can show its result`() {
        // Process death, then relaunch: NavGraph's launch check and the restored role-selection
        // screen start together. The result is already on the server. If the launch check ended
        // the record, the screen would find nothing pending and show a plain catalog.
        backend.setCurrent(path(2, "Data Scientist", "T2"))
        val app = App()
        app.seed(startedAtMillis = now - 60_000)

        runBlocking { app.recovery.resolveAtLaunch() }
        assertNotNull("left for the screen", app.record())
        assertEquals("and not a single request at launch", 0, backend.currentReads)

        val restored = app.screen()
        restored.awaitState<CareerPathRoleSelectionUiState.StillFinishing>()
        restored.visible()

        assertEquals(2, eventually("the found path") { restored.generatedPathId.value })
        assertNull(app.record())
        assertEquals(0, backend.generatePosts)
    }

    @Test
    fun `launch ends an expired record without a request`() {
        val app = App()
        app.seed(startedAtMillis = now - 2 * 60 * 60_000L)
        server.shutdown()

        runBlocking { app.recovery.resolveAtLaunch() }

        assertNull("past the deadline the server has stopped working on it", app.record())
    }

    // ==========================================================================================
    // What counts as "never sent"
    // ==========================================================================================

    @Test
    fun `only a connection that never opened proves the request was not sent`() {
        assertTrue(ConnectException("refused").provesRequestNotSent())
        assertTrue(UnknownHostException("dns").provesRequestNotSent())
        assertTrue(NoRouteToHostException("route").provesRequestNotSent())

        assertFalse(SocketException("Software caused connection abort").provesRequestNotSent())
        assertFalse(SocketException("Connection reset").provesRequestNotSent())
        assertFalse(SocketTimeoutException("timeout").provesRequestNotSent())
        assertFalse(EOFException().provesRequestNotSent())
        assertFalse(IOException("unexpected end of stream").provesRequestNotSent())

        // OkHttp's own retry after a mid-request failure: the last exception is the reconnect's,
        // the one that proves the request got out is attached as suppressed.
        val retriedAfterAbort = ConnectException("failed to connect: EPERM").apply {
            addSuppressed(SocketException("Software caused connection abort"))
        }
        assertFalse(retriedAfterAbort.provesRequestNotSent())
        val neverConnected = ConnectException("refused again").apply { addSuppressed(ConnectException("refused")) }
        assertTrue(neverConnected.provesRequestNotSent())
    }

    private companion object {
        const val CATALOG = """[
            {"family_key": "data_analyst", "display_name": "Data Analyst", "category": "Data & AI", "short_explanation": "Turns data into insights."},
            {"family_key": "data_scientist", "display_name": "Data Scientist", "category": "Data & AI", "short_explanation": "Builds predictive models."}
        ]"""

        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

        fun created(body: String) = json(body).setResponseCode(201)

        /** A CareerPathRead as select_role() returns it, reduced to what identifies it. */
        fun path(id: Int, role: String, updatedAt: String) = """{
            "id": $id, "role_id": null, "custom_role": "$role",
            "role_family_key": "${role.lowercase().replace(' ', '_')}", "display_role_name": "$role",
            "is_primary": true, "generated_via": "catalog",
            "role_overview": {"display_name": "$role", "category": "Data & AI", "short_explanation": "x",
                "responsibilities": [], "core_skills": [], "tools": [], "difficulty": "beginner",
                "entry_level_expectations": "x", "interview_focus": [], "portfolio_expectations": "x"},
            "phases": [], "skill_gaps": [], "resume_connection": null, "interview_prep": [],
            "job_readiness": {"overall_percent": 0, "contributing_signals": 1, "total_signals": 5,
                "skills_percent": 0, "projects_percent": 0, "resume_percent": null, "practice_percent": null,
                "interview_percent": null, "top_focus_areas": [], "is_estimate": false},
            "created_at": "2026-09-26T15:38:32.648043", "updated_at": "$updatedAt"
        }"""
    }
}
