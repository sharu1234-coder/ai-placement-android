package com.aiplacementos.app.voice.realtime

/**
 * A recording transport.
 *
 * Follows the lesson VA-1's mutation run taught: a fake that re-implements the production
 * guard hides the guard's deletion. This one enforces almost nothing. It counts what arrived
 * and lets the test drive the server side, so assertions are about what the *session* did.
 */
class FakeRealtimeTransport : RealtimeTransport {

    /** Returned from the next [connect]. Set it to simulate a refused or failing connection. */
    var connectError: RealtimeError? = null

    /** Returned from [send] and [sendAudio]. Set it to simulate backpressure or a dead socket. */
    var sendResult: RealtimeError? = null

    var connects = 0
        private set
    var disconnects = 0
        private set

    /** Counts arrivals, not accepted work -- see [FakePlayback.writeAttempts] in VA-1. */
    var audioFrames = 0
        private set
    var audioBytes = 0
        private set

    val sent = mutableListOf<RealtimeClientEvent>()

    var lastConfig: RealtimeSessionConfig? = null
        private set

    private var listener: RealtimeTransport.Listener? = null
    private var currentState: TransportState = TransportState.Disconnected

    override val state: TransportState get() = currentState

    override suspend fun connect(
        config: RealtimeSessionConfig,
        listener: RealtimeTransport.Listener,
    ): RealtimeError? {
        connects++
        lastConfig = config
        connectError?.let {
            currentState = TransportState.Disconnected
            return it
        }
        this.listener = listener
        currentState = TransportState.Connected
        return null
    }

    override fun sendAudio(buffer: ByteArray, length: Int): RealtimeError? {
        audioFrames++
        audioBytes += length
        return sendResult
    }

    /**
     * Called inside [send], before it returns.
     *
     * Lets a test observe the world *at the moment* a message goes out, which is the only way
     * to assert ordering between a local side effect and a network send.
     */
    var onSend: ((RealtimeClientEvent) -> Unit)? = null

    override fun send(event: RealtimeClientEvent): RealtimeError? {
        onSend?.invoke(event)
        sent += event
        return sendResult
    }

    override suspend fun disconnect() {
        disconnects++
        currentState = TransportState.Disconnected
        listener = null
    }

    /** Deliver a server event the way a live socket would. */
    fun emit(event: RealtimeServerEvent) {
        listener?.onEvent(event)
    }

    /**
     * Deliver through a listener captured earlier, after the connection it belonged to is gone.
     *
     * This is the only way to test late-event rejection: a real socket can and does call back
     * after its replacement is live, and the generation check is what makes that harmless.
     */
    fun emitThrough(captured: RealtimeTransport.Listener, event: RealtimeServerEvent) {
        captured.onEvent(event)
    }

    fun capturedListener(): RealtimeTransport.Listener? = listener

    fun interrupts(): List<RealtimeClientEvent.Interrupt> =
        sent.filterIsInstance<RealtimeClientEvent.Interrupt>()

    fun commits(): Int = sent.count { it is RealtimeClientEvent.CommitTurn }
}
