package com.nimit.delivery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/*
 * ตัวอัปเดตในแอปของแอป dev (แยกจาก UpdatePrompt ร่วม)
 * เหตุผล: ตัวร่วมอ่าน "30 รีลีสล่าสุด" ของรีโป releases แต่รายการนั้นมีแต่ build-N ของแอปลูกค้า
 * ทำให้ไม่เห็นแท็ก dev-build-N เลย จึงไม่เคยเด้งอัปเดต ที่นี่หาเลข build สูงสุดจากรายการ "แท็ก" แทน
 * (ไม่พึ่งลำดับของรายการรีลีส) แล้วค่อยดึงรีลีสของแท็กนั้นเพื่อเอาลิงก์ APK
 */
private const val DEV_REPO = "est1728/NimitDelivery-releases"
private const val DEV_PREFIX = "dev-build-"

private class DevRemote(val code: Int, val url: String)

private fun devGet(url: String): String? {
    val c = URL(url).openConnection() as HttpURLConnection
    try {
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
    } finally {
        c.disconnect()
    }
}

private suspend fun devFetchLatest(): DevRemote? = withContext(Dispatchers.IO) {
    try {
        var best = -1
        var page = 1
        while (page <= 20) {
            val body = devGet("https://api.github.com/repos/$DEV_REPO/git/matching-refs/tags/$DEV_PREFIX?per_page=100&page=$page")
                ?: return@withContext null
            val refs = JSONArray(body)
            for (i in 0 until refs.length()) {
                val n = refs.getJSONObject(i).getString("ref").removePrefix("refs/tags/$DEV_PREFIX").toIntOrNull() ?: continue
                if (n > best) best = n
            }
            if (refs.length() < 100) break
            page++
        }
        if (best < 0) return@withContext null
        val rel = JSONObject(devGet("https://api.github.com/repos/$DEV_REPO/releases/tags/$DEV_PREFIX$best") ?: return@withContext null)
        val assets = rel.getJSONArray("assets")
        var url = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                url = a.getString("browser_download_url")
                break
            }
        }
        if (url.isEmpty()) null else DevRemote(best, url)
    } catch (_: Exception) {
        null
    }
}

private suspend fun devDownloadApk(ctx: Context, url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
    try {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "update.apk")
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = 0L
                var n = input.read(buf)
                while (n >= 0) {
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0 && done - last > 200_000) {
                        last = done
                        onProgress(done.toFloat() / total)
                    }
                    n = input.read(buf)
                }
            }
        }
        onProgress(1f)
        out
    } catch (_: Exception) {
        null
    }
}

private fun devInstallApk(ctx: Context, f: File) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun DevUpdatePrompt() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var remote by remember { mutableStateOf<DevRemote?>(null) }
    var dismissed by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var failed by remember { mutableStateOf(false) }
    var needPerm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val cur = try {
            PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()
        } catch (_: Exception) {
            Int.MAX_VALUE
        }
        val r = devFetchLatest()
        if (r != null && r.code > cur) remote = r
    }

    val r = remote
    if (r != null && !dismissed) {
        AlertDialog(
            onDismissRequest = { if (progress == null) dismissed = true },
            title = { Text("มีเวอร์ชันใหม่") },
            text = {
                Column {
                    Text("ระบบผู้พัฒนา เวอร์ชัน " + r.code + " พร้อมให้อัปเดตแล้ว")
                    if (needPerm) Text("\nกรุณาอนุญาต \"ติดตั้งแอปจากแหล่งที่ไม่รู้จัก\" ให้แอปนี้ แล้วกลับมากดอัปเดตอีกครั้ง")
                    if (failed) Text("\nดาวน์โหลดไม่สำเร็จ ลองใหม่อีกครั้ง")
                    val p = progress
                    if (p != null) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = progress == null,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
                            needPerm = true
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } else {
                            needPerm = false
                            failed = false
                            progress = 0f
                            scope.launch {
                                val f = devDownloadApk(ctx, r.url) { progress = it }
                                if (f == null) failed = true else devInstallApk(ctx, f)
                                progress = null
                            }
                        }
                    }
                ) { Text(if (progress == null) "อัปเดต" else "กำลังโหลด...") }
            },
            dismissButton = {
                if (progress == null) TextButton(onClick = { dismissed = true }) { Text("ภายหลัง") }
            }
        )
    }
}
