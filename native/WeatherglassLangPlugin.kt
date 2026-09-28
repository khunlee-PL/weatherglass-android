// WeatherglassLangPlugin.kt — the Android twin of weatherglass-ios/native/WeatherglassLang.swift.
//
// THE CONTRACT (fixed by the app, build/weatherglass.html loadLangPack()):
//   window.Capacitor.Plugins.WeatherglassLang.loadLang({code}) -> Promise<{json: string}>
// The base app ships ENGLISH ONLY. Each other language is its own ON-DEMAND Play Asset Delivery pack
// (module lang_<code>, holding lang-<code>.json) — the Play twin of the iOS On-Demand Resource tag
// lang-<code>. Play hosts it; the device fetches it (~10–80 KB) only when the reader picks that
// language. Its sha256 rides inside the signed app in www/lang-manifest.json (Capacitor copies www/
// into assets/public/) and is verified before the JSON is trusted — a pack that does not match is
// refused, never "handled".
//
// A pack already on the device (previous session, or cached by Play) answers without touching the
// network — the conditionallyBeginAccessingResources shortcut on iOS.
//
// Registration: Capacitor auto-registers npm plugins only; this LOCAL class is registered in
// MainActivity (codemagic-android.yaml writes it: registerPlugin(WeatherglassLangPlugin.class)).
//
// Gradle: com.google.android.play:asset-delivery (AssetPackManager) — wired by codemagic-android.yaml.

package live.weatherglass.app

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.AssetPackState
import com.google.android.play.core.assetpacks.AssetPackStateUpdateListener
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

@CapacitorPlugin(name = "WeatherglassLang")
class WeatherglassLangPlugin : Plugin() {

    private val assetPacks: AssetPackManager by lazy { AssetPackManagerFactory.getInstance(context) }

    private fun readAsset(name: String): String? =
        try { context.assets.open("public/$name").bufferedReader().use { it.readText() } }
        catch (e: Exception) { null }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // Resolve an on-demand pack to its assets directory, fetching it if it is not present yet.
    private fun withPack(pack: String, onReady: (File) -> Unit, onFail: (String) -> Unit) {
        assetPacks.getPackLocation(pack)?.assetsPath()?.let { onReady(File(it)); return }
        val listener = object : AssetPackStateUpdateListener {
            override fun onStateUpdate(state: AssetPackState) {
                if (state.name() != pack) return
                when (state.status()) {
                    AssetPackStatus.COMPLETED -> {
                        assetPacks.unregisterListener(this)
                        val dir = assetPacks.getPackLocation(pack)?.assetsPath()
                        if (dir != null) onReady(File(dir)) else onFail("pack '$pack' completed but has no location")
                    }
                    AssetPackStatus.FAILED -> { assetPacks.unregisterListener(this); onFail("pack '$pack' download failed (code ${state.errorCode()})") }
                    AssetPackStatus.CANCELED -> { assetPacks.unregisterListener(this); onFail("pack '$pack' download was canceled") }
                    else -> { /* PENDING / DOWNLOADING / TRANSFERRING — keep waiting */ }
                }
            }
        }
        assetPacks.registerListener(listener)
        assetPacks.fetch(listOf(pack)).addOnFailureListener { e ->
            assetPacks.unregisterListener(listener)
            onFail("could not start fetch for '$pack': ${e.message}")
        }
    }

    @PluginMethod
    fun loadLang(call: PluginCall) {
        val code = call.getString("code") ?: ""
        if (!Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,4})?$").matches(code))
            return call.reject("loadLang needs a language code")
        // No manifest digest = no trust: the manifest is part of the signed app.
        val wantSha = readAsset("lang-manifest.json")
            ?.let { JSONObject(it).optJSONObject("langs")?.optJSONObject(code)?.optString("sha256") }
            ?.takeIf { it.isNotEmpty() }
            ?: return call.reject("no digest for $code in lang-manifest.json")
        val pack = "lang_" + code.replace('-', '_')        // Android split names: no hyphens
        withPack(pack,
            onReady = { dir ->
                val f = File(dir, "lang-$code.json")
                val bytes = try { f.readBytes() } catch (e: Exception) { call.reject("language resource not found after download"); return@withPack }
                val got = sha256(bytes)
                if (got != wantSha.lowercase()) { call.reject("language refused: sha256 mismatch for $code"); return@withPack }
                call.resolve(JSObject().put("json", String(bytes, Charsets.UTF_8)))
            },
            onFail = { why -> call.reject("language download failed: $why") })
    }
}
