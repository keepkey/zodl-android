package co.electriccoin.zcash.ui.common.provider

import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashPCZTActionAck
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignedPCZT
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * ZA-67: Unit tests for KeepKeySigningProtocol — the USB signing exchange state machine.
 *
 * Each test wires up a FakeKeepKeyTransportProvider that returns scripted responses, then
 * verifies the protocol drives the correct message sequence and handles errors.
 */
class KeepKeySigningProtocolTest {

    // --- helpers ---

    private fun ackBytes(nextIndex: Int): ByteArray =
        ZcashPCZTActionAck.newBuilder().setNextIndex(nextIndex).build().toByteArray()

    private fun signedPcztBytes(vararg sigs: ByteArray): ByteArray =
        ZcashSignedPCZT.newBuilder()
            .also { b -> sigs.forEach { b.addSignatures(com.google.protobuf.ByteString.copyFrom(it)) } }
            .build()
            .toByteArray()

    // --- zero-action path ---

    @Test
    fun zeroActionsReturnsEmptySignatureList() = runBlocking {
        val transport = FakeTransport(
            listOf(MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)))
        )
        val protocol = KeepKeySigningProtocol(transport)
        val sigs = protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 0)
        assertTrue(sigs.isEmpty())
    }

    @Test
    fun zeroActionsOnlyOneMessageSent() = runBlocking {
        val transport = FakeTransport(
            listOf(MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)))
        )
        val protocol = KeepKeySigningProtocol(transport)
        protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 0)
        assertEquals(1, transport.callCount)
    }

    // --- single-action path ---

    @Test
    fun singleActionCollectsSignature() = runBlocking {
        val sig = ByteArray(64) { 0xAB.toByte() }
        val transport = FakeTransport(
            listOf(
                MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)),
                MSG_ZCASH_PCZT_ACTION to Pair(MSG_ZCASH_SIGNED_PCZT, signedPcztBytes(sig)),
            )
        )
        val protocol = KeepKeySigningProtocol(transport)
        val sigs = protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 1)
        assertEquals(1, sigs.size)
        assertContentEquals(sig, sigs[0])
    }

    // --- multi-action path ---

    @Test
    fun twoActionsWithIntermediateAck() = runBlocking {
        val sig0 = ByteArray(64) { 0x11.toByte() }
        val sig1 = ByteArray(64) { 0x22.toByte() }
        val transport = FakeTransport(
            listOf(
                MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)),
                MSG_ZCASH_PCZT_ACTION to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(1)),
                MSG_ZCASH_PCZT_ACTION to Pair(MSG_ZCASH_SIGNED_PCZT, signedPcztBytes(sig0, sig1)),
            )
        )
        val protocol = KeepKeySigningProtocol(transport)
        val sigs = protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 2)
        assertEquals(2, sigs.size)
        assertContentEquals(sig0, sigs[0])
        assertContentEquals(sig1, sigs[1])
    }

    // --- error paths ---

    @Test
    fun failureOnSignPcztThrows() {
        runBlocking {
            val transport = FakeTransport(
                listOf(MSG_ZCASH_SIGN_PCZT to Pair(MSG_FAILURE, ByteArray(0)))
            )
            val protocol = KeepKeySigningProtocol(transport)
            assertFailsWith<KeepKeyTransportException> {
                protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 0)
            }
        }
    }

    @Test
    fun wrongAckTypeOnSignPcztThrowsIllegalState() {
        runBlocking {
            val transport = FakeTransport(
                listOf(MSG_ZCASH_SIGN_PCZT to Pair(9999, ByteArray(0)))
            )
            val protocol = KeepKeySigningProtocol(transport)
            assertFailsWith<IllegalStateException> {
                protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 0)
            }
        }
    }

    @Test
    fun failureOnActionThrows() {
        runBlocking {
            val transport = FakeTransport(
                listOf(
                    MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)),
                    MSG_ZCASH_PCZT_ACTION to Pair(MSG_FAILURE, ByteArray(0)),
                )
            )
            val protocol = KeepKeySigningProtocol(transport)
            assertFailsWith<KeepKeyTransportException> {
                protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 1)
            }
        }
    }

    @Test
    fun unexpectedResponseTypeOnActionThrows() {
        runBlocking {
            val transport = FakeTransport(
                listOf(
                    MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)),
                    MSG_ZCASH_PCZT_ACTION to Pair(9999, ByteArray(0)),
                )
            )
            val protocol = KeepKeySigningProtocol(transport)
            assertFailsWith<IllegalStateException> {
                protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 1)
            }
        }
    }

    @Test
    fun outOfOrderActionIndexThrows() {
        runBlocking {
            // Device requests action 1 but host expected 0 — protocol violation
            val transport = FakeTransport(
                listOf(
                    MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(1)),
                )
            )
            val protocol = KeepKeySigningProtocol(transport)
            assertFailsWith<IllegalStateException> {
                protocol.sign(accountIndex = 0, pcztBytes = ByteArray(4), nActions = 1)
            }
        }
    }

    // --- message content checks ---

    @Test
    fun correctAccountIndexIsSentInInitRequest() = runBlocking {
        val transport = FakeTransport(
            listOf(MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)))
        )
        val protocol = KeepKeySigningProtocol(transport)
        protocol.sign(accountIndex = 7, pcztBytes = ByteArray(4), nActions = 0)
        val sent = com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignPCZT.parseFrom(transport.sentPayloads[0])
        assertEquals(7, sent.account)
    }

    @Test
    fun pcztBytesAreSentVerbatim() = runBlocking {
        val pcztBytes = ByteArray(16) { (it + 1).toByte() }
        val transport = FakeTransport(
            listOf(MSG_ZCASH_SIGN_PCZT to Pair(MSG_ZCASH_PCZT_ACTION_ACK, ackBytes(0)))
        )
        val protocol = KeepKeySigningProtocol(transport)
        protocol.sign(accountIndex = 0, pcztBytes = pcztBytes, nActions = 0)
        val sent = com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashSignPCZT.parseFrom(transport.sentPayloads[0])
        assertContentEquals(pcztBytes, sent.pcztData.toByteArray())
    }

    // --- fake transport ---

    /**
     * Script-driven fake: each entry maps an expected outgoing typeId → response (typeId, payload).
     * Records all (typeId, payload) pairs sent for inspection.
     */
    private class FakeTransport(
        private val script: List<Pair<Int, Pair<Int, ByteArray>>>,
    ) : KeepKeyTransportProvider {
        private var index = 0
        var callCount = 0
        val sentPayloads = mutableListOf<ByteArray>()
        val sentTypeIds = mutableListOf<Int>()

        override suspend fun sendMessage(typeId: Int, payload: ByteArray): Pair<Int, ByteArray> {
            sentTypeIds += typeId
            sentPayloads += payload
            val (expectedType, response) = script[index++]
            check(typeId == expectedType) {
                "FakeTransport: expected typeId $expectedType but got $typeId"
            }
            callCount++
            return response
        }

        override suspend fun requestPermission(): Boolean = true
        override suspend fun connect(): KeepKeyDevice = KeepKeyDevice(null, 0, 0, 0)
        override suspend fun disconnect() = Unit
        override fun isConnected(): Boolean = true
    }
}
