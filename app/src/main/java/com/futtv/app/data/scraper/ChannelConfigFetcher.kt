package com.futtv.app.data.scraper

import android.util.Log
import com.futtv.app.data.model.Channel
import com.futtv.app.data.model.ChannelRegistry
import com.futtv.app.data.model.StreamServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Descarga channels.json desde el repo de GitHub y reconstruye los servidores
 * de cada canal con los keys y dominio actualizados.
 *
 * Si el sitio cambia un stream key o el dominio de streamhdx, solo hay que
 * actualizar channels.json en GitHub — sin publicar una nueva APK.
 */
object ChannelConfigFetcher {

    private const val TAG = "ChannelConfigFetcher"
    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/XxZman/futtv/main/channels.json"

    fun fetch(client: OkHttpClient): List<Channel>? = try {
        val request = Request.Builder()
            .url(CONFIG_URL)
            .header("Cache-Control", "no-cache")
            .build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null
        parse(body).also { Log.d(TAG, "Remote config loaded: ${it.size} channels") }
    } catch (e: Exception) {
        Log.w(TAG, "Remote config unavailable, using hardcoded fallback: ${e.message}")
        null
    }

    private fun parse(json: String): List<Channel> {
        val root = JSONObject(json)
        val hdxDomain = root.optString("hdx_domain", "streamhdx.com")
        val arr = root.getJSONArray("channels")

        // id → stream key
        val keyMap = buildMap {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                put(obj.getString("id"), obj.getString("key"))
            }
        }

        return ChannelRegistry.channels.map { channel ->
            val key = keyMap[channel.id] ?: return@map channel   // sin cambio si no está en el JSON
            channel.copy(servers = buildServers(hdxDomain, key))
        }
    }

    private fun buildServers(hdxDomain: String, streamKey: String): List<StreamServer> =
        (1..3).mapIndexed { index, live ->
            StreamServer(
                label = "Reproductor $live",
                url   = "https://$hdxDomain/live$live.php?stream=$streamKey",
                index = index
            )
        }
}
