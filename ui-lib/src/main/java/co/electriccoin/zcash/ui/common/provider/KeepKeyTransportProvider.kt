package co.electriccoin.zcash.ui.common.provider

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val KEEPKEY_VID = 0x2B24
private const val KEEPKEY_PID = 0x0001

private const val USB_TIMEOUT_MS = 10_000

data class KeepKeyDevice(
    val serialNumber: String?,
    val majorVersion: Int,
    val minorVersion: Int,
    val patchVersion: Int,
)

interface KeepKeyTransportProvider {
    suspend fun requestPermission(): Boolean

    suspend fun connect(): KeepKeyDevice

    suspend fun disconnect()

    suspend fun sendMessage(typeId: Int, payload: ByteArray): Pair<Int, ByteArray>

    fun isConnected(): Boolean
}

class KeepKeyTransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

@Suppress("TooManyFunctions")
class KeepKeyTransportProviderImpl(
    private val context: Context
) : KeepKeyTransportProvider {
    private val mutex = Mutex()
    private var connection: UsbDeviceConnection? = null
    private var iface: UsbInterface? = null
    private var epIn: UsbEndpoint? = null
    private var epOut: UsbEndpoint? = null

    override suspend fun requestPermission(): Boolean =
        withContext(Dispatchers.IO) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val device = findKeepKey(usbManager) ?: return@withContext false
            if (usbManager.hasPermission(device)) return@withContext true

            suspendCancellableCoroutine { cont ->
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            if (ACTION_USB_PERMISSION != intent.action) return
                            runCatching { context.unregisterReceiver(this) }
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            if (cont.isActive) cont.resume(granted)
                        }
                    }
                ContextCompat.registerReceiver(
                    context,
                    receiver,
                    IntentFilter(ACTION_USB_PERMISSION),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                val pendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent(ACTION_USB_PERMISSION),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                usbManager.requestPermission(device, pendingIntent)
            }
        }

    override suspend fun connect(): KeepKeyDevice =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val device =
                    findKeepKey(usbManager)
                        ?: throw KeepKeyTransportException("No KeepKey device found")

                if (!usbManager.hasPermission(device)) {
                    throw KeepKeyTransportException(
                        "USB permission not granted — call UsbManager.requestPermission() first"
                    )
                }

                val selectedIface =
                    findHidInterface(device)
                        ?: throw KeepKeyTransportException("KeepKey HID interface not found")

                val (inEp, outEp) =
                    findEndpoints(selectedIface)
                        ?: throw KeepKeyTransportException("KeepKey interrupt endpoints not found")

                val conn =
                    usbManager.openDevice(device)
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
            if (transferred < 0) {
                throw KeepKeyTransportException("USB write failed (bulkTransfer returned $transferred)")
            }
        }
    }

    private fun buildPackets(typeId: Int, payload: ByteArray): List<ByteArray> =
        buildKeepKeyPackets(typeId, payload)

    private fun readPackets(conn: UsbDeviceConnection, ep: UsbEndpoint): Pair<Int, ByteArray> {
        val first = ByteArray(PACKET_SIZE)
        val n = conn.bulkTransfer(ep, first, PACKET_SIZE, USB_TIMEOUT_MS)
        if (n < 0) throw KeepKeyTransportException("USB read failed (bulkTransfer returned $n)")
        return parseKeepKeyPackets(first) {
            val cont = ByteArray(PACKET_SIZE)
            val r = conn.bulkTransfer(ep, cont, PACKET_SIZE, USB_TIMEOUT_MS)
            if (r < 0) throw KeepKeyTransportException("USB read continuation failed")
            cont
        }
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

                1 -> {
                    i += 8
                }

                // 64-bit (skip)
                5 -> {
                    i += 4
                }

                // 32-bit (skip)
                else -> {
                    break
                }
            }
        }
        return KeepKeyDevice(serial, major, minor, patch)
    }

    @Suppress("MagicNumber")
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
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    inEp = ep
                } else {
                    outEp = ep
                }
            }
        }
        return if (inEp != null && outEp != null) Pair(inEp, outEp) else null
    }

    private companion object {
        const val MSG_TYPE_GET_FEATURES = 55
        const val ACTION_USB_PERMISSION = "co.electriccoin.zcash.keepkey.USB_PERMISSION"
    }
}
