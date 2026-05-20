package co.electriccoin.zcash.ui.common.provider

import com.keepkey.deviceprotocol.KeepKeyMessage.DebugLinkDecision
import com.keepkey.deviceprotocol.KeepKeyMessage.LoadDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// 10.0.2.2 is the alias to the development machine's loopback inside an Android emulator (AVD).
// When running on a physical device or CI with host networking, override with the actual host IP.
const val EMULATOR_BRIDGE_DEFAULT_URL = "http://10.0.2.2:5000"

private const val HTTP_TIMEOUT_MS = 30_000
private const val MSG_TYPE_GET_FEATURES = 55
private const val MSG_TYPE_LOAD_DEVICE = 13
private const val MSG_TYPE_DEBUG_LINK_DECISION = 100

/**
 * KeepKeyTransportProvider that drives the KeepKey firmware emulator via the Flask HTTP bridge
 * in scripts/emulator/bridge.py (image: kktech/kkemu:latest).
 *
 * The bridge exposes two endpoints per interface:
 *   POST /exchange/main {"data":"<64-byte-packet-as-hex>"} → writes to UDP 11044
 *   GET  /exchange/main → reads from UDP 11044, returns {"data":"<hex>"}
 *   (same for /exchange/debug, UDP 11045)
 *
 * HID framing (buildKeepKeyPackets / parseKeepKeyPackets) is identical to the USB transport.
 * This class is primarily intended for instrumented tests running against the Docker emulator.
 *
 * Do NOT register this in ProviderModule — it is for test and development use only.
 */
class KeepKeyEmulatorTransportProvider(
    private val baseUrl: String = EMULATOR_BRIDGE_DEFAULT_URL,
) : KeepKeyTransportProvider {

    @Volatile private var connected = false

    override suspend fun requestPermission(): Boolean = true

    override suspend fun connect(): KeepKeyDevice =
        withContext(Dispatchers.IO) {
            val (_, featuresBytes) = mainExchange(MSG_TYPE_GET_FEATURES, ByteArray(0))
            connected = true
            parseFeatures(featuresBytes)
        }

    override suspend fun disconnect() {
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override suspend fun sendMessage(typeId: Int, payload: ByteArray): Pair<Int, ByteArray> =
        withContext(Dispatchers.IO) {
            mainExchange(typeId, payload)
        }

    // --- Internal helpers ---

    private fun mainExchange(typeId: Int, payload: ByteArray): Pair<Int, ByteArray> =
        bridgeExchange("/exchange/main", typeId, payload)

    private fun bridgeExchange(path: String, typeId: Int, payload: ByteArray): Pair<Int, ByteArray> {
        for (packet in buildKeepKeyPackets(typeId, payload)) {
            httpPost(path, packet.toHex())
        }
        val first = httpGet(path).fromHex()
        return parseKeepKeyPackets(first) { httpGet(path).fromHex() }
    }

    private fun httpPost(path: String, hexData: String) {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write("""{"data":"$hexData"}""".toByteArray(Charsets.UTF_8)) }
            check(conn.responseCode == 200) { "Bridge POST $path returned ${conn.responseCode}" }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(path: String): String {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.requestMethod = "GET"
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            // bridge.py returns {"data":"<hex>"} — extract without a JSON library
            return body.substringAfter("\"data\":\"").substringBefore("\"")
        } finally {
            conn.disconnect()
        }
    }

    // Minimal proto2 varint parser — mirrors the one in KeepKeyTransportProviderImpl.
    // Extracts device_id (tag 1), major_version (tag 2), minor_version (tag 3), patch_version (tag 4)
    // from the Features message returned by GetFeatures.
    @Suppress("MagicNumber")
    private fun parseFeatures(bytes: ByteArray): KeepKeyDevice {
        var major = 0; var minor = 0; var patch = 0; var serial: String? = null
        var i = 0
        while (i < bytes.size) {
            val (tagWord, tLen) = readVarint(bytes, i); i += tLen
            val tag = (tagWord shr 3).toInt()
            when ((tagWord and 7).toInt()) {
                0 -> {
                    val (v, n) = readVarint(bytes, i); i += n
                    when (tag) { 2 -> major = v.toInt(); 3 -> minor = v.toInt(); 4 -> patch = v.toInt() }
                }
                2 -> {
                    val (len, n) = readVarint(bytes, i); i += n
                    if (tag == 1 && i + len.toInt() <= bytes.size) {
                        serial = String(bytes, i, len.toInt(), Charsets.UTF_8)
                    }
                    i += len.toInt()
                }
                1 -> i += 8
                5 -> i += 4
                else -> break
            }
        }
        return KeepKeyDevice(serial, major, minor, patch)
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var r = 0L; var shift = 0; var i = start
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            r = r or ((b and 0x7F).toLong() shl shift); shift += 7
            if (b and 0x80 == 0) break
        }
        return r to (i - start)
    }

    companion object {
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
        private fun String.fromHex(): ByteArray {
            check(length % 2 == 0) { "Odd-length hex string" }
            return ByteArray(length / 2) { i ->
                ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
            }
        }
    }
}

/**
 * Test control channel for the KeepKey emulator's debug link (UDP 11045 via bridge.py).
 *
 * Allows tests to load a known mnemonic and simulate button presses without physical
 * interaction. Only usable when the emulator is running with DebugLink enabled (the default
 * in the Docker image).
 *
 * Do NOT use in production code.
 */
class KeepKeyEmulatorDebugLink(
    private val baseUrl: String = EMULATOR_BRIDGE_DEFAULT_URL,
) {
    /**
     * Load a BIP-39 mnemonic into the emulator over the main wire, then confirm the
     * "Wipe device?" prompt via the debug link.
     *
     * After this call the emulator is ready to sign with the given seed.
     */
    fun loadDevice(
        mnemonic: String,
        pin: String = "",
        label: String = "emulator",
    ) {
        val req = LoadDevice.newBuilder()
            .setMnemonic(mnemonic)
            .setPin(pin)
            .setPassphraseProtection(false)
            .setLabel(label)
            .setSkipChecksum(true)
            .build()

        // Send LoadDevice on the main wire — device may show a "Wipe?" confirmation.
        for (packet in buildKeepKeyPackets(MSG_TYPE_LOAD_DEVICE, req.toByteArray())) {
            httpPost("/exchange/main", packet.toHex())
        }

        // Confirm via the debug link (bypasses the physical button requirement).
        pressYes()

        // Drain the Success/Features reply from the main wire so the buffer is clean.
        runCatching { httpGet("/exchange/main") }
    }

    /** Simulate pressing the physical "Confirm" button on the device. */
    fun pressYes() = pressButton(yes = true)

    /** Simulate pressing the physical "Cancel" button on the device. */
    fun pressNo() = pressButton(yes = false)

    private fun pressButton(yes: Boolean) {
        val decision = DebugLinkDecision.newBuilder().setYesNo(yes).build()
        for (packet in buildKeepKeyPackets(MSG_TYPE_DEBUG_LINK_DECISION, decision.toByteArray())) {
            httpPost("/exchange/debug", packet.toHex())
        }
    }

    private fun httpPost(path: String, hexData: String) {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.use { it.write("""{"data":"$hexData"}""".toByteArray(Charsets.UTF_8)) }
            check(conn.responseCode == 200) { "Bridge POST $path returned ${conn.responseCode}" }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(path: String): String {
        val conn = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = HTTP_TIMEOUT_MS
            conn.readTimeout = HTTP_TIMEOUT_MS
            conn.requestMethod = "GET"
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            return body.substringAfter("\"data\":\"").substringBefore("\"")
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val HTTP_TIMEOUT_MS = 30_000
        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
