package co.electriccoin.zcash.ui.common.provider

import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashGetOrchardFVK
import com.keepkey.deviceprotocol.KeepKeyMessageZcash.ZcashOrchardFVK
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * ZA-70: Instrumented integration tests against the KeepKey firmware emulator.
 *
 * These tests require the Docker emulator to be running and reachable. Start it with:
 *   cd keepkey-firmware/scripts/emulator && docker-compose up
 *
 * The emulator bridge listens on port 5000 on the host machine. When running on an Android
 * emulator (AVD), 10.0.2.2 routes to the host's loopback. Override EMULATOR_BRIDGE_DEFAULT_URL
 * or set the system property "keepkey.emulator.url" to target a different address.
 *
 * Tests are automatically skipped if the bridge is unreachable, so they are safe to leave in the
 * test suite and will not break CI without the emulator.
 */
class KeepKeyEmulatorIntegrationTest {
    private val bridgeUrl: String =
        System.getProperty("keepkey.emulator.url") ?: EMULATOR_BRIDGE_DEFAULT_URL

    private val transport = KeepKeyEmulatorTransportProvider(bridgeUrl)
    private val debugLink = KeepKeyEmulatorDebugLink(bridgeUrl)

    @Before
    fun skipIfEmulatorUnreachable() {
        Assume.assumeTrue(
            "KeepKey emulator not reachable at $bridgeUrl — start docker-compose up in scripts/emulator/",
            isBridgeReachable(bridgeUrl),
        )
    }

    // --- Orchard FVK export ---

    @Test
    fun orchardFvkMatchesGoldenVectors() =
        runBlocking {
            // Golden values from test_msg_zcash_orchard.py REFERENCE_FVK_ALL_MNEMONIC,
            // computed by the orchard Rust crate for mnemonic "all all all ... all" (12x), account 0.
            val expectedAk = "057ab051d4fbb0205d28648bacbc6471b533476c27beca33e5b9f511d855672b"
            val expectedNk = "34a35a0bda50273b0319afa7a70f86b6b162eb311d263d8f6321def00228ba25"
            val expectedRivk = "46bd2bd5e6eca5ef03e18cd76595519ea96706c5826a93ba4dca947d711a7c0a"

            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            // ZIP-32 path m/32'/133'/0'
            val req =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 0)
                    .build()

            val (typeId, fvkBytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, req.toByteArray())
            assertEquals(MSG_ZCASH_ORCHARD_FVK, typeId, "Expected ZcashOrchardFVK response type")

            val fvk = ZcashOrchardFVK.parseFrom(fvkBytes)
            assertEquals(expectedAk, fvk.ak.toByteArray().toHex(), "ak mismatch")
            assertEquals(expectedNk, fvk.nk.toByteArray().toHex(), "nk mismatch")
            assertEquals(expectedRivk, fvk.rivk.toByteArray().toHex(), "rivk mismatch")
        }

    @Test
    fun orchardFvkComponentsAre32Bytes() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val req =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 0)
                    .build()

            val (_, fvkBytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, req.toByteArray())
            val fvk = ZcashOrchardFVK.parseFrom(fvkBytes)

            assertEquals(32, fvk.ak.size(), "ak must be 32 bytes")
            assertEquals(32, fvk.nk.size(), "nk must be 32 bytes")
            assertEquals(32, fvk.rivk.size(), "rivk must be 32 bytes")
        }

    @Test
    fun orchardFvkAkSignBitIsZero() =
        runBlocking {
            // ak encodes a Pallas point in compressed form; the sign bit (MSB of last byte) must be 0
            // for canonical form per the Zcash spec § 4.2.3.
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val req =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 0)
                    .build()

            val (_, fvkBytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, req.toByteArray())
            val fvk = ZcashOrchardFVK.parseFrom(fvkBytes)
            val ak = fvk.ak.toByteArray()

            assertEquals(0, (ak[31].toInt() and 0x80), "ak sign bit must be 0 (canonical Pallas point)")
        }

    @Test
    fun orchardFvkIsDeterministic() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val req =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 0)
                    .build()
            val bytes = req.toByteArray()

            val (_, fvk1Bytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, bytes)
            val (_, fvk2Bytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, bytes)

            val fvk1 = ZcashOrchardFVK.parseFrom(fvk1Bytes)
            val fvk2 = ZcashOrchardFVK.parseFrom(fvk2Bytes)

            assertEquals(fvk1.ak.toByteArray().toHex(), fvk2.ak.toByteArray().toHex(), "ak must be deterministic")
            assertEquals(fvk1.nk.toByteArray().toHex(), fvk2.nk.toByteArray().toHex(), "nk must be deterministic")
            assertEquals(fvk1.rivk.toByteArray().toHex(), fvk2.rivk.toByteArray().toHex(), "rivk must be deterministic")
        }

    @Test
    fun differentAccountsProduceDifferentFvks() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val req0 =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 0)
                    .build()
            val req1 =
                ZcashGetOrchardFVK
                    .newBuilder()
                    .addAddressN(HARDENED or 32)
                    .addAddressN(HARDENED or 133)
                    .addAddressN(HARDENED or 1)
                    .build()

            val (_, fvk0Bytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, req0.toByteArray())
            val (_, fvk1Bytes) = transport.sendMessage(MSG_ZCASH_GET_ORCHARD_FVK, req1.toByteArray())

            val ak0 =
                ZcashOrchardFVK
                    .parseFrom(fvk0Bytes)
                    .ak
                    .toByteArray()
                    .toHex()
            val ak1 =
                ZcashOrchardFVK
                    .parseFrom(fvk1Bytes)
                    .ak
                    .toByteArray()
                    .toHex()

            assert(ak0 != ak1) { "Different accounts must produce different ak values" }
        }

    // --- Connect / device info ---

    @Test
    fun connectReturnsNonNullDevice() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            val device = transport.connect()
            assertNotNull(device)
        }

    @Test
    fun connectReturnsExpectedFirmwareVersion() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            val device = transport.connect()
            // Emulator ships firmware 7.14.x; we require >= 7.14.0 for ZCash support.
            assert(device.majorVersion == 7 && device.minorVersion >= 14) {
                "Expected firmware >= 7.14 but got ${device.majorVersion}.${device.minorVersion}.${device.patchVersion}"
            }
        }

    // --- PCZT signing (ZA-71) ---

    @Test
    fun singleActionSigningReturnsOneSig() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val protocol = KeepKeySigningProtocol(transport)

            val sigs =
                protocol.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(makeAction()),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            assertEquals(1, sigs.size, "Expected 1 signature for 1 action")
        }

    @Test
    fun singleActionSignatureIs64Bytes() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val protocol = KeepKeySigningProtocol(transport)

            val sigs =
                protocol.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(makeAction(sighashByte = 0xAB.toByte())),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            assertEquals(64, sigs[0].size, "RedPallas signature must be exactly 64 bytes")
        }

    @Test
    fun singleActionSignatureIsNonZero() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val protocol = KeepKeySigningProtocol(transport)

            val sigs =
                protocol.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(makeAction()),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            assertFalse(sigs[0].all { it == 0.toByte() }, "Signature must not be all-zero")
        }

    @Test
    fun multipleActionsReturnCorrectCount() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val protocol = KeepKeySigningProtocol(transport)

            val sigs =
                protocol.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions =
                        listOf(
                            makeAction(sighashByte = 0x11.toByte()),
                            makeAction(sighashByte = 0x22.toByte()),
                            makeAction(sighashByte = 0x33.toByte()),
                        ),
                    totalAmount = 30_000L,
                    fee = 1_000L,
                )

            assertEquals(3, sigs.size, "Expected 3 signatures for 3 actions")
            sigs.forEachIndexed { i, sig -> assertEquals(64, sig.size, "Signature[$i] must be 64 bytes") }
        }

    @Test
    fun differentSighashesProduceDifferentSignatures() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val protocol1 = KeepKeySigningProtocol(transport)
            val sigs1 =
                protocol1.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(makeAction(sighashByte = 0xAA.toByte())),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            // Reload device so the session is clean for the second call.
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()

            val protocol2 = KeepKeySigningProtocol(transport)
            val sigs2 =
                protocol2.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(makeAction(sighashByte = 0xBB.toByte())),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            assertFalse(
                sigs1[0].contentEquals(sigs2[0]),
                "Different sighashes must produce different signatures",
            )
        }

    @Test
    fun differentAccountsProduceDifferentSignatures() =
        runBlocking {
            val sighash = ByteArray(32) { 0xCD.toByte() }
            val alpha = ByteArray(32) { 0x01.toByte() }

            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val sigs0 =
                KeepKeySigningProtocol(transport).sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    actions = listOf(OrchardActionData(alpha = alpha, sighash = sighash, value = 10_000L)),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val sigs1 =
                KeepKeySigningProtocol(transport).sign(
                    accountIndex = 1,
                    pcztBytes = ByteArray(0),
                    actions = listOf(OrchardActionData(alpha = alpha, sighash = sighash, value = 10_000L)),
                    totalAmount = 10_000L,
                    fee = 1_000L,
                )

            assertFalse(
                sigs0[0].contentEquals(sigs1[0]),
                "Different account indices must produce different signatures",
            )
        }

    @Test
    fun zeroActionsReturnsEmptyList() =
        runBlocking {
            debugLink.loadDevice(MNEMONIC_ALL_ALL)
            transport.connect()
            val protocol = KeepKeySigningProtocol(transport)

            val sigs =
                protocol.sign(
                    accountIndex = 0,
                    pcztBytes = ByteArray(0),
                    nActions = 0,
                )

            assertTrue(sigs.isEmpty(), "Zero actions must return empty signature list")
        }

    // --- Constants ---

    private companion object {
        // BIP-39 test mnemonic: 12x "all" (same as python-keepkey's setup_mnemonic_allallall)
        const val MNEMONIC_ALL_ALL =
            "all all all all all all all all all all all all"

        // ZIP-32 hardened index offset
        const val HARDENED = 0x80000000.toInt()

        // ZCash message type IDs (from messages.proto)
        const val MSG_ZCASH_GET_ORCHARD_FVK = 1304
        const val MSG_ZCASH_ORCHARD_FVK = 1305

        fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

        /**
         * Build a minimal [OrchardActionData] for use in signing tests.
         * [alphaByte] and [sighashByte] fill all 32 bytes of their respective fields.
         */
        fun makeAction(
            alphaByte: Byte = 0x01.toByte(),
            sighashByte: Byte = 0xAB.toByte(),
            value: Long = 10_000L,
            isSpend: Boolean = true,
        ) = OrchardActionData(
            alpha = ByteArray(32) { alphaByte },
            sighash = ByteArray(32) { sighashByte },
            value = value,
            isSpend = isSpend,
        )

        fun isBridgeReachable(baseUrl: String): Boolean =
            runCatching {
                val conn = URL("$baseUrl/health").openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2_000
                conn.readTimeout = 2_000
                conn.requestMethod = "GET"
                val ok = conn.responseCode == 200
                conn.disconnect()
                ok
            }.getOrDefault(false)
    }
}
