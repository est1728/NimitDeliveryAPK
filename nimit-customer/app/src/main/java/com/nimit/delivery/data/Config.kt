package com.nimit.delivery.data

object Config {
    /**
     * เปิดหน้า OTP (Firebase Phone Auth)
     * ต้องทำก่อนเปิด: เพิ่มแอป Android (com.nimit.delivery + SHA-1) ใน Firebase,
     * ใส่ google-services.json, เปิด Phone sign-in และแผน Blaze
     */
    const val OTP_ENABLED = false
}
