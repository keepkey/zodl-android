package co.electriccoin.zcash.ui.common.provider

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val KEEPKEY_VID = 0x2B24
private const val KEEPKEY_PID = 0x0001

private const val PACKET_SIZE = 64
private const val FRAME_MARKER = 0x3F.toByte()

// First packet: 1 (marker) + 2 (type) + 4 (length) = 7 header bytes → 57 payload bytes
private const val FIRST_PACKET_PAYLOAD = 57

// Continuation packets: 1 (marker) → 63 payload bytes
private const val CONT_PACKET_PAYLOAD = 63

private const val USB_TIMEOUT_MS = 10_000

data class KeepKeyDevice(
    val serialNumber: String?,
    val majorVersion: Int,
    val minorVersion: Int,
    val patchVersion: Int,
)

interface KeepKeyTransportProvider {
    suspend fun connect(context: Context): KeepKeyDevice
    suspend fun disconnect()
    suspend fun sendMessage(typeId: Int, payload: ByteArray): Pair<Int, ByteArray>
    fun isConnected(): Boolean
}

class KeepKeyTransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

class KeepKeyTransportProviderImpl : KeepKeyTransportProvider {
    private val mutex = Mutex()
    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    override suspend fun connect(context: Context): KeepKeyDevice =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val device = findKeepKey(usbManager)
                    ?: throw KeepKeyTransportException("No KeepKey device found")

                if (!usbManager.hasPermission(device)) {
                    throw KeepKeyTransportException(
                        "USB permission not granted — call UsbManager.requestPermission() first"
                    )
                }

                val selectedIface = findHidInterface(device)
                    ?: throw KeepKeyTransportException("KeepKey HID interface not found")

                val (inEp, outEp) = findEndpoints(selectedIface)
                    ?: throw KeepKeyTransportException("KeepKey interrupt endpoints not found")

                val conn = usbManager.openDevice(device)
                    ?: throw KeepKeyTransportException("Failed to open USB device connection")

                if (!conn.claimInterface(selectedIface, true)) {
                    conn.close()
                    throw KeepKeyTransportException("Failed to claim HID interface")
                }

                connection = conn
                iface = selectedIface
                epIn = inEp
                epOut = outEp

                // Read device features to get firmware version
                val featuresPayload = readFeatures(conn, inEp, outEp)
                featuresPayload
            }
        }

    override suspend fun disconnect() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val conn = connection ?: return@withContext
                val i = iface
                if (i != null) conn.releaseInterface(i)
                conn.close()
                connection = null
                iface = null
                epIn = null
                epOut = null
            }
        }

    override fun isConnected(): Boolean = connection != null

    override suspend fun sendMessage(typeId: Int, payload: ByteArray): Pair<Int, ByteArray> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val conn = connection ?: throw KeepKeyTransportException("Not connected")
                val out = epOut ?: throw KeepKeyTransportException("No OUT endpoint")
                val inEp = epIn ?: throw KeepKeyTransportException("No IN endpoint")

                writePackets(conn, out, typeId, payload)
                readPackets(conn, inEp)
            }
        }

    // --- HID framing ---

    private fun writePackets(conn: UsbDeviceConnection, ep: UsbEndpoint, typeId: Int, payload: ByteArray) {
        val packets = buildPackets(typeId, payload)
        for (packet in packets) {
            val transferred = conn.bulkTransfer(ep, packet, packet.size, USB_TIMEOUT_MS)
            if (transferred < 0) throw KeepKeyTransportException("USB write failed (bulkTransfer returned $transferred)")
        }
    }

    private fun buildPackets(typeId: Int, payload: ByteArray): List<ByteArray> {
        val packets = mutableListOf<ByteArray>()

        // First packet: marker + type (2) + length (4) + up to 57 payload bytes
        val first = ByteArray(PACKET_SIZE)
        first[0] = FRAME_MARKER
        first[1] = ((typeId shr 8) and 0xFF).toByte()
        first[2] = (typeId and 0xFF).toByte()
        first[3] = ((payload.size shr 24) and 0xFF).toByte()
        first[4] = ((payload.size shr 16) and 0xFF).toByte()
        first[5] = ((payload.size shr 8) and 0xFF).toByte()
        first[6] = (payload.size and 0xFF).toByte()
        val firstChunk = minOf(FIRST_PACKET_PAYLOAD, payload.size)
        System.arraycopy(payload, 0, first, 7, firstChunk)
        packets.add(first)

        // Continuation packets
        var offset = firstChunk
        while (offset < payload.size) {
            val cont = ByteArray(PACKET_SIZE)
            cont[0] = FRAME_MARKER
            val chunk = minOf(CONT_PACKET_PAYLOAD, payload.size - offset)
            System.arraycopy(payload, offset, cont, 1, chunk)
            packets.add(cont)
            offset += chunk
        }

        return packets
    }

    private fun readPackets(conn: UsbDeviceConnection, ep: UsbEndpoint): Pair<Int, ByteArray> {
        val first = ByteArray(PACKET_SIZE)
        val n = conn.bulkTransfer(ep, first, PACKET_SIZE, USB_TIMEOUT_MS)
        if (n < 0) throw KeepKeyTransportException("USB read failed (bulkTransfer returned $n)")
        if (first[0] != FRAME_MARKER) throw KeepKeyTransportException("Invalid framing marker: 0x${first[0].toInt().and(0xFF).toString(16)}")

        val typeId = ((first[1].toInt() and 0xFF) shl 8) or (first[2].toInt() and 0xFF)
        val totalLen = ((first[3].toInt() and 0xFF) shl 24) or
            ((first[4].toInt() and 0xFF) shl 16) or
            ((first[5].toInt() and 0xFF) shl 8) or
            (first[6].toInt() and 0xFF)

        val buffer = ByteArray(totalLen)
        val firstChunk = minOf(FIRST_PACKET_PAYLOAD, totalLen)
        System.arraycopy(first, 7, buffer, 0, firstChunk)

        var received = firstChunk
        while (received < totalLen) {
            val cont = ByteArray(PACKET_SIZE)
            val r = conn.bulkTransfer(ep, cont, PACKET_SIZE, USB_TIMEOUT_MS)
            if (r < 0) throw KeepKeyTransportException("USB read continuation failed")
            if (cont[0] != FRAME_MARKER) throw KeepKeyTransportException("Invalid continuation marker")
            val chunk = minOf(CONT_PACKET_PAYLOAD, totalLen - received)
            System.arraycopy(cont, 1, buffer, received, chunk)
            received += chunk
        }

        return Pair(typeId, buffer)
    }

    // GetFeatures (type 55 in messages.proto) → Features (type 17)
    // We send an empty GetFeatures and parse the version fields from the response.
    private fun readFeatures(
        conn: UsbDeviceConnection,
        inEp: UsbEndpoint,
        outEp: UsbEndpoint,
    ): KeepKeyDevice {
        val emptyGetFeatures = ByteArray(0)
        writePackets(conn, outEp, MSG_TYPE_GET_FEATURES, emptyGetFeatures)
        val (_, featuresBytes) = readPackets(conn, inEp)
        return parseFeatures(featuresBytes)
    }

    // Minimal proto2 varint parser to extract version fields from Features message.
    // Fields: major_version (tag 2, varint), minor_version (tag 3, varint), patch_version (tag 4, varint)
    // device_id (tag 1, length-delimited)
    @Suppress("MagicNumber")
    private fun parseFeatures(bytes: ByteArray): KeepKeyDevice {
        var major = 0
        var minor = 0
        var patch = 0
        var serial: String? = null
        var i = 0
        while (i < bytes.size) {
            val tagByte = readVarint(bytes, i)
            i += tagByte.second
            val tag = (tagByte.first shr 3).toInt()
            val wireType = (tagByte.first and 0x07).toInt()
            when (wireType) {
                0 -> { // varint
                    val v = readVarint(bytes, i)
                    i += v.second
                    when (tag) {
                        2 -> major = v.first.toInt()
                        3 -> minor = v.first.toInt()
                        4 -> patch = v.first.toInt()
                    }
                }
                2 -> { // length-delimited
                    val len = readVarint(bytes, i)
                    i += len.second
                    val start = i
                    i += len.first.toInt()
                    if (tag == 1 && i <= bytes.size) {
                        serial = String(bytes, start, len.first.toInt(), Charsets.UTF_8)
                    }
                }
                1 -> i += 8   // 64-bit (skip)
                5 -> i += 4   // 32-bit (skip)
                else -> break
            }
        }
        return KeepKeyDevice(serial, major, minor, patch)
    }

    private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var i = start
        while (i < bytes.size) {
            val b = bytes[i++].toInt() and 0xFF
            result = result or ((b and 0x7F).toLong() shl shift)
            shift += 7
            if (b and 0x80 == 0) break
        }
        return Pair(result, i - start)
    }

    private fun findKeepKey(usbManager: UsbManager): UsbDevice? =
        usbManager.deviceList.values.find {
            it.vendorId == KEEPKEY_VID && it.productId == KEEPKEY_PID
        }

    private fun findHidInterface(device: UsbDevice): UsbInterface? =
        (0 until device.interfaceCount)
            .map { device.getInterface(it) }
            .find { it.interfaceClass == UsbConstants.USB_CLASS_HID }

    private fun findEndpoints(iface: UsbInterface): Pair<UsbEndpoint, UsbEndpoint>? {
        var inEp: UsbEndpoint? = null
        var outEp: UsbEndpoint? = null
        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (ep.direction == UsbConstants.USB_DIR_IN) inEp = ep
                else outEp = ep
            }
        }
        return if (inEp != null && outEp != null) Pair(inEp, outEp) else null
    }

    private companion object {
        const val MSG_TYPE_GET_FEATURES = 55
    }
}
