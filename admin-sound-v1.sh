#!/bin/bash
# ใช้ไฟล์เสียง 7_skip.wav เป็นเสียงแจ้งเตือนหลักของแอดมิน (แทนระฆังสังเคราะห์)
# ไฟล์ต้องอยู่ที่ app/src/admin/res/raw/admin_order_chime.wav (คำสั่งที่ให้ไว้ก๊อปมาให้แล้ว)
# เล่นผ่านช่องเสียงเดิม (ดังแม้ปิดเสียงเครื่อง ปรับความดังในแอป วนซ้ำ/รอบเดียวตามที่ตั้ง) ถ้าอ่านไฟล์ไม่ได้จะกลับไปใช้เสียงสังเคราะห์  รันซ้ำได้
set -u
die() { echo "หยุด: $1"; exit 1; }
J=app/src/admin/java/com/nimit/delivery
A=$J/ui/AdminAlert.kt
W=app/src/admin/res/raw/admin_order_chime.wav
[ -f $A ] || die "ไม่เจอ $A (ต้องรัน admin-notify-v1.sh ก่อน)"
grep -q 'object AdminAlert' $A || die "AdminAlert.kt ไม่ใช่เวอร์ชันที่คาด"
[ -f $W ] || die "ไม่เจอไฟล์เสียง $W (ก๊อปจาก Downloads มาก่อน ดูคำสั่งในแชท)"
[ "$(head -c 4 $W)" = "RIFF" ] || die "ไฟล์ $W ไม่ใช่ WAV"
SZ=$(wc -c < $W)
echo "ไฟล์เสียง: $SZ ไบต์"

# ---------- ตัวอ่านไฟล์ WAV (PCM 16 บิต โมโน/สเตอริโอ) ----------
cat > $J/ui/AdminSound.kt <<'KT_EOF'
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
KT_EOF
echo "AdminSound.kt: เขียนแล้ว"

# ---------- ต่อเข้ากับตัวเล่นเสียง ----------
if grep -q 'AdminSound' $A; then
  echo "AdminAlert: แก้แล้ว ข้าม"
else
  grep -q '^    private val pcm: ShortArray by lazy { build() }$' $A || die "AdminAlert: ไม่เจอจุดยึด pcm"
  grep -q 'setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)' $A || die "AdminAlert: ไม่เจอจุดยึดรูปแบบเสียง"
  grep -q 't.setLoopPoints(0, pcm.size, -1)' $A || die "AdminAlert: ไม่เจอจุดยึด loop"
  grep -q '(pcm.size \* 1000L / SR) + 200' $A || die "AdminAlert: ไม่เจอจุดยึดความยาวเสียง"
  grep -q '^            boost(ctx)$' $A || die "AdminAlert: ไม่เจอจุดยึด boost"
  sed -i 's|^    private val pcm: ShortArray by lazy { build() }$|    private val synth: ShortArray by lazy { build() }\n    private val pcm: ShortArray get() = AdminSound.pcm ?: synth\n    private val rate: Int get() = if (AdminSound.pcm != null) AdminSound.rate else SR\n    private val chans: Int get() = if (AdminSound.pcm != null) AdminSound.channels else 1|' $A
  sed -i 's|setSampleRate(SR).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)|setSampleRate(rate).setChannelMask(if (chans == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO)|' $A
  sed -i 's|t.setLoopPoints(0, pcm.size, -1)|t.setLoopPoints(0, pcm.size / chans, -1)|' $A
  sed -i 's|(pcm.size \* 1000L / SR) + 200|(pcm.size / chans * 1000L / rate) + 200|g' $A
  sed -i 's|^\( *\)boost(ctx)$|\1AdminSound.prepare(ctx)\n\1boost(ctx)|' $A
  # ปุ่มทดลองเสียงแสดง "กำลังเล่น" ตามความยาวเสียงจริง (~5 วินาที)
  sed -i 's|LaunchedEffect(playing) { if (playing) { delay(1900); playing = false } }|LaunchedEffect(playing) { if (playing) { delay(5300); playing = false } }|' $A
  grep -q 'AdminSound' $A || die "แก้ AdminAlert ไม่สำเร็จ"
  echo "AdminAlert: แก้แล้ว"
fi

echo "---- ผลตรวจ ----"
o=$(tr -cd '{' < $A | wc -c); c=$(tr -cd '}' < $A | wc -c); p1=$(tr -cd '(' < $A | wc -c); p2=$(tr -cd ')' < $A | wc -c)
echo "$A {} $o/$c () $p1/$p2"
grep -c 'AdminSound.prepare(ctx)' $A | sed 's/^/จุดที่เตรียมเสียงก่อนเล่น (ควรเป็น 3): /'
grep -n 'chans\|rate' $A | cut -c1-140 | head -12
echo "เสร็จ"
