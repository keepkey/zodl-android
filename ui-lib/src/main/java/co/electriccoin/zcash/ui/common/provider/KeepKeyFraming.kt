package co.electriccoin.zcash.ui.common.provider

internal const val PACKET_SIZE = 64
internal const val FRAME_MARKER = 0x3F.toByte()

// First packet: 1 (marker) + 2 (type) + 4 (length) = 7 header bytes → 57 payload bytes
internal const val FIRST_PACKET_PAYLOAD = 57

// Continuation packets: 1 (marker) → 63 payload bytes
internal const val CONT_PACKET_PAYLOAD = 63

/**
 * Encode [typeId] and [payload] as a list of 64-byte HID packets following the KeepKey framing
 * protocol. The first packet carries a 7-byte header (marker | type | length); subsequent packets
 * carry a 1-byte marker followed by up to 63 payload bytes.
 */
@Suppress("MagicNumber")
internal fun buildKeepKeyPackets(typeId: Int, payload: ByteArray): List<ByteArray> {
    val packets = mutableListOf<ByteArray>()

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

/**
 * Parse a sequence of 64-byte HID packets (as produced by [buildKeepKeyPackets]) back into
 * a `(typeId, payload)` pair. [nextPacket] is called once per continuation packet needed.
 *
 * @throws KeepKeyTransportException if framing markers are invalid.
 */
@Suppress("MagicNumber")
internal fun parseKeepKeyPackets(
    firstPacket: ByteArray,
    nextPacket: () -> ByteArray,
): Pair<Int, ByteArray> {
    if (firstPacket[0] != FRAME_MARKER) {
        throw KeepKeyTransportException(
            "Invalid framing marker: 0x${firstPacket[0].toInt().and(0xFF).toString(16)}"
        )
    }

    val typeId = ((firstPacket[1].toInt() and 0xFF) shl 8) or (firstPacket[2].toInt() and 0xFF)
    val totalLen =
        ((firstPacket[3].toInt() and 0xFF) shl 24) or
            ((firstPacket[4].toInt() and 0xFF) shl 16) or
            ((firstPacket[5].toInt() and 0xFF) shl 8) or
            (firstPacket[6].toInt() and 0xFF)

    val buffer = ByteArray(totalLen)
    val firstChunk = minOf(FIRST_PACKET_PAYLOAD, totalLen)
    System.arraycopy(firstPacket, 7, buffer, 0, firstChunk)

    var received = firstChunk
    while (received < totalLen) {
        val cont = nextPacket()
        if (cont[0] != FRAME_MARKER) {
            throw KeepKeyTransportException("Invalid continuation marker")
        }
        val chunk = minOf(CONT_PACKET_PAYLOAD, totalLen - received)
        System.arraycopy(cont, 1, buffer, received, chunk)
        received += chunk
    }

    return Pair(typeId, buffer)
}
