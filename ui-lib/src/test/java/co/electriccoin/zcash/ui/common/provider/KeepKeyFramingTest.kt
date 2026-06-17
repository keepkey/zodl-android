package co.electriccoin.zcash.ui.common.provider

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * ZA-66: Unit tests for KeepKey HID packet framing (build + parse round-trips).
 *
 * Protocol recap: all packets are 64 bytes.
 *   First packet : [0x3F | typeHi | typeLo | lenB3 | lenB2 | lenB1 | lenB0 | 57 payload bytes]
 *   Continuation : [0x3F | 63 payload bytes]
 */
class KeepKeyFramingTest {
    // --- buildKeepKeyPackets ---

    @Test
    fun emptyPayloadProducesSinglePacket() {
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = ByteArray(0))
        assertEquals(1, packets.size)
        val p = packets[0]
        assertEquals(64, p.size)
        assertEquals(0x3F.toByte(), p[0]) // marker
        assertEquals(0x00.toByte(), p[1]) // type hi
        assertEquals(0x01.toByte(), p[2]) // type lo
        assertEquals(0x00.toByte(), p[3]) // len bytes = 0
        assertEquals(0x00.toByte(), p[4])
        assertEquals(0x00.toByte(), p[5])
        assertEquals(0x00.toByte(), p[6])
    }

    @Test
    fun singlePacketPayloadFitsIn57Bytes() {
        val payload = ByteArray(57) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0510, payload = payload)
        assertEquals(1, packets.size)
        val p = packets[0]
        assertEquals(0x3F.toByte(), p[0])
        assertEquals(0x05.toByte(), p[1])
        assertEquals(0x10.toByte(), p[2])
        assertEquals(0x00.toByte(), p[3])
        assertEquals(0x00.toByte(), p[4])
        assertEquals(0x00.toByte(), p[5])
        assertEquals(57.toByte(), p[6])
        assertContentEquals(payload, p.copyOfRange(7, 64))
    }

    @Test
    fun payloadOf58BytesRequiresTwoPackets() {
        val payload = ByteArray(58) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = payload)
        assertEquals(2, packets.size)

        // First packet carries bytes 0..56
        assertContentEquals(payload.copyOfRange(0, 57), packets[0].copyOfRange(7, 64))

        // Continuation packet: marker + 1 byte of payload, rest zero-padded
        assertEquals(0x3F.toByte(), packets[1][0])
        assertEquals(payload[57], packets[1][1])
    }

    @Test
    fun payloadExactly120BytesRequiresTwoPackets() {
        // 57 + 63 = 120 — fits exactly in two packets
        val payload = ByteArray(120) { (it * 3).toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0514, payload = payload)
        assertEquals(2, packets.size)
        assertContentEquals(payload.copyOfRange(0, 57), packets[0].copyOfRange(7, 64))
        assertContentEquals(payload.copyOfRange(57, 120), packets[1].copyOfRange(1, 64))
    }

    @Test
    fun payloadOf121BytesRequiresThreePackets() {
        val payload = ByteArray(121) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0514, payload = payload)
        assertEquals(3, packets.size)
        assertContentEquals(payload.copyOfRange(0, 57), packets[0].copyOfRange(7, 64))
        assertContentEquals(payload.copyOfRange(57, 120), packets[1].copyOfRange(1, 64))
        assertEquals(payload[120], packets[2][1])
    }

    @Test
    fun typeIdEncodedBigEndian() {
        val packets = buildKeepKeyPackets(typeId = 0x1234, payload = ByteArray(0))
        assertEquals(0x12.toByte(), packets[0][1])
        assertEquals(0x34.toByte(), packets[0][2])
    }

    @Test
    fun payloadLengthEncodedBigEndianUInt32() {
        val len = 0x01_02_03_04
        val packets = buildKeepKeyPackets(typeId = 0, payload = ByteArray(len))
        val p = packets[0]
        assertEquals(0x01.toByte(), p[3])
        assertEquals(0x02.toByte(), p[4])
        assertEquals(0x03.toByte(), p[5])
        assertEquals(0x04.toByte(), p[6])
    }

    @Test
    fun allPacketsAreExactly64Bytes() {
        val payload = ByteArray(200) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = payload)
        for (pkt in packets) assertEquals(64, pkt.size)
    }

    @Test
    fun allPacketsHaveFrameMarker() {
        val payload = ByteArray(200) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = payload)
        for (pkt in packets) assertEquals(0x3F.toByte(), pkt[0])
    }

    // --- parseKeepKeyPackets ---

    @Test
    fun roundTripEmptyPayload() {
        val packets = buildKeepKeyPackets(typeId = 42, payload = ByteArray(0))
        val iter = packets.iterator()
        val (typeId, payload) = parseKeepKeyPackets(iter.next()) { iter.next() }
        assertEquals(42, typeId)
        assertEquals(0, payload.size)
    }

    @Test
    fun roundTripSinglePacket() {
        val original = ByteArray(20) { (it + 1).toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0514, payload = original)
        val iter = packets.iterator()
        val (typeId, payload) = parseKeepKeyPackets(iter.next()) { iter.next() }
        assertEquals(0x0514, typeId)
        assertContentEquals(original, payload)
    }

    @Test
    fun roundTripMultiPacket() {
        val original = ByteArray(200) { (it * 7).toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0512, payload = original)
        val iter = packets.iterator()
        val (typeId, payload) = parseKeepKeyPackets(iter.next()) { iter.next() }
        assertEquals(0x0512, typeId)
        assertContentEquals(original, payload)
    }

    @Test
    fun roundTripExact57Bytes() {
        val original = ByteArray(57) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = original)
        val iter = packets.iterator()
        val (_, payload) = parseKeepKeyPackets(iter.next()) { iter.next() }
        assertContentEquals(original, payload)
    }

    @Test
    fun roundTripExact120Bytes() {
        val original = ByteArray(120) { it.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0002, payload = original)
        val iter = packets.iterator()
        val (_, payload) = parseKeepKeyPackets(iter.next()) { iter.next() }
        assertContentEquals(original, payload)
    }

    @Test
    fun parseThrowsOnBadFirstMarker() {
        val bad = ByteArray(64) { 0x00 }
        assertFailsWith<KeepKeyTransportException> {
            parseKeepKeyPackets(bad) { error("unexpected") }
        }
    }

    @Test
    fun parseThrowsOnBadContinuationMarker() {
        val payload = ByteArray(200) { 0xAA.toByte() }
        val packets = buildKeepKeyPackets(typeId = 0x0001, payload = payload).toMutableList()
        // Corrupt the second packet's marker
        packets[1][0] = 0x00
        val iter = packets.iterator()
        assertFailsWith<KeepKeyTransportException> {
            parseKeepKeyPackets(iter.next()) { iter.next() }
        }
    }
}
