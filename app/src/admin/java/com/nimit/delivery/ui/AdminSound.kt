package com.nimit.delivery.ui

import android.content.Context
import com.nimit.delivery.R

/** โหลดเสียงแจ้งเตือนจาก res/raw/admin_order_chime.wav เป็น PCM พร้อมเล่นด้วย AudioTrack */
object AdminSound {
    @Volatile var pcm: ShortArray? = null
    @Volatile var rate: Int = 44100
    @Volatile var channels: Int = 2
    private var tried = false

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int): Int = le16(b, o) or (le16(b, o + 2) shl 16)

    fun prepare(ctx: Context) {
        if (tried) return
        tried = true
        try {
            val bytes: ByteArray = ctx.applicationContext.resources.openRawResource(R.raw.admin_order_chime).use { it.readBytes() }
            var pos = 12
            var fmtOk = false
            var ch = 2
            var sr = 44100
            var bits = 16
            var dataStart = -1
            var dataLen = 0
            while (pos + 8 <= bytes.size) {
                val id = String(bytes, pos, 4, Charsets.US_ASCII)
                val len = le32(bytes, pos + 4)
                if (id == "fmt ") {
                    fmtOk = le16(bytes, pos + 8) == 1
                    ch = le16(bytes, pos + 10)
                    sr = le32(bytes, pos + 12)
                    bits = le16(bytes, pos + 22)
                } else if (id == "data") {
                    dataStart = pos + 8
                    dataLen = minOf(len, bytes.size - dataStart)
                    break
                }
                if (len < 0) break
                pos += 8 + len + (len and 1)
            }
            if (!fmtOk || bits != 16 || dataStart < 0 || (ch != 1 && ch != 2) || dataLen <= 0) return
            val n: Int = dataLen / 2
            val out = ShortArray(n - (n % ch))
            for (i in out.indices) out[i] = le16(bytes, dataStart + 2 * i).toShort()
            rate = sr
            channels = ch
            pcm = out
        } catch (e: Exception) {
            pcm = null
        }
    }
}
