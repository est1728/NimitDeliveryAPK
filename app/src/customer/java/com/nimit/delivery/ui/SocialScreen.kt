package com.nimit.delivery.ui

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.nimit.delivery.data.Session

private const val SOCIAL_URL = "https://nimitdelivery.vercel.app/nimit-social.html"

/** Nimit Social: ใช้หน้าเว็บเดียวกับเว็บไซต์ แต่เปิดในแอป (ไม่เด้งออก) และส่งเบอร์ลูกค้าเข้าไปให้ล็อกอินอัตโนมัติ */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SocialScreen(session: Session, onBack: () -> Unit) {
    val phone = session.customerPhone.orEmpty()
    val url = if (phone.isNotEmpty()) "$SOCIAL_URL?phone=${Uri.encode(phone)}" else SOCIAL_URL
    var pick: ValueCallback<Array<Uri>>? = null
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pick?.onReceiveValue(if (uri != null) arrayOf(uri) else null); pick = null
    }
    val holder = remember { arrayOfNulls<WebView>(1) }
    BackHandler { val w = holder[0]; if (w != null && w.canGoBack()) w.goBack() else onBack() }
    Box(Modifier.fillMaxSize()) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            WebView(ctx).apply {
                holder[0] = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        val u = r.url.toString()
                        // กลับหน้าหลัก/หน้าอื่นของเรา -> ออกจากหน้าโซเชียลกลับแอป
                        if (u.contains("shops.html")) { onBack(); return true }
                        return false
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                        pick?.onReceiveValue(null); pick = cb
                        chooser.launch("image/*"); return true
                    }
                }
                loadUrl(url)
            }
        }, onRelease = { it.destroy() })
    }
}

fun NavGraphBuilder.socialRoute(session: Session, nav: NavController) {
    composable("social") { SocialScreen(session) { nav.popBackStack() } }
}
