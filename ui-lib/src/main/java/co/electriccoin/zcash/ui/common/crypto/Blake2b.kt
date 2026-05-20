package co.electriccoin.zcash.ui.common.crypto

import java.lang.Long.parseUnsignedLong

// Pure-Kotlin BLAKE2b-512 per RFC 7693.
// Used for F4Jumble (ZIP-316) in UFVK encoding — no Android-API-level restrictions.
@Suppress("MagicNumber")
internal object Blake2b {
    // Kotlin rejects hex literals > Long.MAX_VALUE, so use parseUnsignedLong to get
    // the correct two's-complement bit patterns for the BLAKE2b initialization vectors.
    private val IV = longArrayOf(
        parseUnsignedLong("6a09e667f3bcc908", 16), parseUnsignedLong("bb67ae8584caa73b", 16),
        parseUnsignedLong("3c6ef372fe94f82b", 16), parseUnsignedLong("a54ff53a5f1d36f1", 16),
        parseUnsignedLong("510e527fade682d1", 16), parseUnsignedLong("9b05688c2b3e6c1f", 16),
        parseUnsignedLong("1f83d9abfb41bd6b", 16), parseUnsignedLong("5be0cd19137e2179", 16),
    )

    private val SIGMA = arrayOf(
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
        intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
        intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
        intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
        intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
        intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
        intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
        intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
        intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
        intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
    )

    /**
     * Compute BLAKE2b-512 with optional key and personalization.
     * key must be 0–64 bytes; personal must be exactly 16 bytes.
     */
    fun hash(
        message: ByteArray,
        key: ByteArray = ByteArray(0),
        personal: ByteArray = ByteArray(16),
    ): ByteArray {
        require(key.size <= 64) { "BLAKE2b key exceeds 64 bytes" }
        require(personal.size == 16) { "BLAKE2b personalization must be 16 bytes" }

        // Build 64-byte parameter block.
        val p = ByteArray(64)
        p[0] = 64          // digest size
        p[1] = key.size.toByte()
        p[2] = 1           // fanout
        p[3] = 1           // max depth
        System.arraycopy(personal, 0, p, 48, 16)

        // h[i] = IV[i] XOR p[i*8..(i+1)*8] as little-endian u64
        val h = LongArray(8) { i -> IV[i] xor leToLong(p, i * 8) }

        // If a key is given, prepend it as a 128-byte block.
        val data: ByteArray = if (key.isNotEmpty()) {
            val kb = ByteArray(128)
            System.arraycopy(key, 0, kb, 0, key.size)
            kb + message
        } else {
            message
        }

        // Process 128-byte blocks.
        val numBlocks = ((data.size + 127) / 128).coerceAtLeast(1)
        val block = ByteArray(128)
        for (i in 0 until numBlocks) {
            val start = i * 128
            val end = minOf(start + 128, data.size)
            block.fill(0)
            if (end > start) System.arraycopy(data, start, block, 0, end - start)
            val counter = if (i == numBlocks - 1) data.size.toLong() else ((i + 1) * 128L)
            compress(h, block, counter, i == numBlocks - 1)
        }

        return ByteArray(64) { i -> ((h[i / 8] shr ((i % 8) * 8)) and 0xFF).toByte() }
    }

    private fun compress(h: LongArray, block: ByteArray, counter: Long, last: Boolean) {
        val v = LongArray(16)
        for (i in 0..7) v[i] = h[i]
        for (i in 0..7) v[8 + i] = IV[i]
        v[12] = v[12] xor counter
        if (last) v[14] = v[14].inv()

        val m = LongArray(16) { i -> leToLong(block, i * 8) }
        for (r in 0..11) {
            val s = SIGMA[r]
            mix(v, 0, 4, 8, 12, m[s[0]], m[s[1]])
            mix(v, 1, 5, 9, 13, m[s[2]], m[s[3]])
            mix(v, 2, 6, 10, 14, m[s[4]], m[s[5]])
            mix(v, 3, 7, 11, 15, m[s[6]], m[s[7]])
            mix(v, 0, 5, 10, 15, m[s[8]], m[s[9]])
            mix(v, 1, 6, 11, 12, m[s[10]], m[s[11]])
            mix(v, 2, 7, 8, 13, m[s[12]], m[s[13]])
            mix(v, 3, 4, 9, 14, m[s[14]], m[s[15]])
        }
        for (i in 0..7) h[i] = h[i] xor v[i] xor v[8 + i]
    }

    private fun mix(v: LongArray, a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] = v[a] + v[b] + x; v[d] = (v[d] xor v[a]).rotateRight(32)
        v[c] = v[c] + v[d];     v[b] = (v[b] xor v[c]).rotateRight(24)
        v[a] = v[a] + v[b] + y; v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] = v[c] + v[d];     v[b] = (v[b] xor v[c]).rotateRight(63)
    }

    private fun leToLong(buf: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0..7) v = v or ((buf[off + i].toLong() and 0xFF) shl (i * 8))
        return v
    }
}
