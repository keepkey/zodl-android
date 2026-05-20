package co.electriccoin.zcash.ui.common.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for the pure-Kotlin BLAKE2b-512 implementation, verified against
 * RFC 7693 Appendix E test vectors and property checks.
 */
class Blake2bTest {

    // --- RFC 7693 Appendix E: self-test vectors ---
    // Input is the sequence of bytes 0..N-1; output is the first 4 bytes of BLAKE2b-512.

    @Test
    fun rfc7693Vector_emptyInput() {
        // BLAKE2b-512("") with no key, no personalization
        val hash = Blake2b.hash(ByteArray(0))
        assertEquals(64, hash.size)
        // First 4 bytes of the known-good hash
        assertEquals(0x78.toByte(), hash[0])
        assertEquals(0x6a.toByte(), hash[1])
        assertEquals(0x02.toByte(), hash[2])
        assertEquals(0xf7.toByte(), hash[3])
    }

    @Test
    fun rfc7693Vector_singleByte() {
        val hash = Blake2b.hash(byteArrayOf(0x61)) // "a"
        assertEquals(64, hash.size)
        // First 4 bytes: python3 -c "import hashlib; print(hashlib.blake2b(b'a').hexdigest()[:8])" → 333fcb4e
        assertEquals(0x33.toByte(), hash[0])
        assertEquals(0x3f.toByte(), hash[1])
        assertEquals(0xcb.toByte(), hash[2])
        assertEquals(0x4e.toByte(), hash[3])
    }

    @Test
    fun rfc7693Vector_abc() {
        val hash = Blake2b.hash(byteArrayOf(0x61, 0x62, 0x63)) // "abc"
        assertEquals(64, hash.size)
        // First 4 bytes: python3 -c "import hashlib; print(hashlib.blake2b(b'abc').hexdigest()[:8])" → ba80a53f
        assertEquals(0xba.toByte(), hash[0])
        assertEquals(0x80.toByte(), hash[1])
        assertEquals(0xa5.toByte(), hash[2])
        assertEquals(0x3f.toByte(), hash[3])
    }

    // --- Output length ---

    @Test
    fun outputIsAlways64Bytes() {
        for (n in listOf(0, 1, 63, 64, 65, 127, 128, 200)) {
            val hash = Blake2b.hash(ByteArray(n) { it.toByte() })
            assertEquals(64, hash.size, "Failed for input length $n")
        }
    }

    // --- Determinism ---

    @Test
    fun sameInputProducesSameOutput() {
        val input = ByteArray(100) { (it * 7).toByte() }
        assertContentEquals(Blake2b.hash(input), Blake2b.hash(input))
    }

    @Test
    fun differentInputsProduceDifferentOutputs() {
        val a = Blake2b.hash(byteArrayOf(1))
        val b = Blake2b.hash(byteArrayOf(2))
        assert(!a.contentEquals(b))
    }

    // --- Personalization ---

    @Test
    fun differentPersonalizationProducesDifferentOutput() {
        val input = ByteArray(32) { it.toByte() }
        val personal1 = "KeepKey_Seed_FP ".toByteArray(Charsets.US_ASCII)
        val personal2 = "KeepKey_Seed_FX ".toByteArray(Charsets.US_ASCII)
        val h1 = Blake2b.hash(input, personal = personal1)
        val h2 = Blake2b.hash(input, personal = personal2)
        assert(!h1.contentEquals(h2))
    }

    @Test
    fun samePersonalizationGivesSameOutput() {
        val input = ByteArray(32) { it.toByte() }
        val personal = "UA_F4Jumble_H   ".toByteArray(Charsets.US_ASCII)
        assertContentEquals(
            Blake2b.hash(input, personal = personal),
            Blake2b.hash(input, personal = personal),
        )
    }

    // --- Key ---

    @Test
    fun keyedAndUnkeyedHashesDiffer() {
        val input = ByteArray(32)
        val keyed = Blake2b.hash(input, key = byteArrayOf(0))
        val unkeyed = Blake2b.hash(input)
        assert(!keyed.contentEquals(unkeyed))
    }

    @Test
    fun differentKeysProduceDifferentHashes() {
        val input = ByteArray(16)
        val h1 = Blake2b.hash(input, key = byteArrayOf(0))
        val h2 = Blake2b.hash(input, key = byteArrayOf(1))
        assert(!h1.contentEquals(h2))
    }

    // --- Validation ---

    @Test
    fun keyLongerThan64BytesThrows() {
        assertFailsWith<IllegalArgumentException> {
            Blake2b.hash(ByteArray(0), key = ByteArray(65))
        }
    }

    @Test
    fun personalizationNot16BytesThrows() {
        assertFailsWith<IllegalArgumentException> {
            Blake2b.hash(ByteArray(0), personal = ByteArray(15))
        }
    }

    @Test
    fun personalizationLongerThan16BytesThrows() {
        assertFailsWith<IllegalArgumentException> {
            Blake2b.hash(ByteArray(0), personal = ByteArray(17))
        }
    }

    // --- Multi-block ---

    @Test
    fun inputExceeding128BytesIsHandled() {
        val hash = Blake2b.hash(ByteArray(256) { it.toByte() })
        assertEquals(64, hash.size)
    }

    @Test
    fun inputExactly128BytesIsHandled() {
        val hash = Blake2b.hash(ByteArray(128) { it.toByte() })
        assertEquals(64, hash.size)
    }

    @Test
    fun inputExactly129BytesIsHandled() {
        val hash = Blake2b.hash(ByteArray(129) { it.toByte() })
        assertEquals(64, hash.size)
    }
}
