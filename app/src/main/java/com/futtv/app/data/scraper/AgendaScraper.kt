package com.futtv.app.data.scraper

import android.util.Log
import com.futtv.app.data.model.ChannelRegistry
import com.futtv.app.data.model.EventStatus
import com.futtv.app.data.model.SportEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

class AgendaScraper(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "AgendaScraper"
        private val AR_TZ = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")
    }

    fun scrapeAgenda(): List<SportEvent> {
        return scrapePelotaLibre().also {
            Log.d(TAG, "Total agenda events: ${it.size}")
        }
    }

    private fun scrapePelotaLibre(): List<SportEvent> {
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = AR_TZ
            }.format(Date())

            val request = Request.Builder()
                .url("https://pelotalibretv.su/agenda/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string() ?: return emptyList()
            }

            val doc = Jsoup.parse(body, "https://pelotalibretv.su/agenda/")
            val events = mutableListOf<SportEvent>()
            var index = 0

            doc.select("ul.menu > li").forEach { li ->
                try {
                    val anchor = li.selectFirst("a") ?: return@forEach
                    val timeStr = anchor.selectFirst("span.t")?.text()?.trim() ?: return@forEach
                    val fullText = anchor.ownText().trim()
                    if (fullText.isBlank()) return@forEach

                    val colonIdx = fullText.indexOf(":")
                    val leagueRaw = if (colonIdx > 0) fullText.substring(0, colonIdx).trim() else "Fútbol"
                    val matchPart = if (colonIdx > 0) fullText.substring(colonIdx + 1).trim() else fullText

                    val vsSplit = matchPart.split(Regex("\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE))
                    if (vsSplit.size < 2) return@forEach

                    val homeTeam = vsSplit[0].trim()
                    val awayTeam = vsSplit[1].trim()
                    if (homeTeam.isBlank() || awayTeam.isBlank()) return@forEach

                    val channelNames = li.select("li.subitem1 a").map { it.ownText().trim() }
                    val pageChannels = channelNames.mapNotNull { matchChannel(it) }.distinctBy { it.id }
                    // Usar solo los canales que lista la página; si no hay ninguno conocido → fallback por liga
                    val finalChannels = pageChannels.ifEmpty {
                        ChannelRegistry.getChannelsForLeague(detectLeague(leagueRaw))
                    }

                    events.add(SportEvent(
                        id = "agenda_${index++}",
                        homeTeam = homeTeam,
                        awayTeam = awayTeam,
                        league = detectLeague(leagueRaw),
                        sport = "Soccer",
                        dateTimeUtc = convertArTimeToUtc(today, timeStr),
                        status = EventStatus.UPCOMING,
                        channels = finalChannels
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing li: ${e.message}")
                }
            }
            Log.d(TAG, "Agenda scraped: ${events.size} events")
            events
        } catch (e: Exception) {
            Log.e(TAG, "agenda error: ${e.message}")
            emptyList()
        }
    }

    private fun convertArTimeToUtc(date: String, timeStr: String): String {
        // pelotalibretv.su shows times in CET (UTC+1); Argentina is UTC-3 → subtract 4h
        return try {
            val parts = timeStr.split(":")
            val h = parts[0].toInt()
            val m = parts[1].toInt()
            val arHour = (h - 4 + 24) % 24
            "${date}T${String.format("%02d:%02d", arHour, m)}:00"
        } catch (e: Exception) {
            "${date}T${timeStr}:00"
        }
    }

    private fun matchChannel(name: String): com.futtv.app.data.model.Channel? {
        val lower = name.lowercase().trim()
        return when {
            // ── Canales sin stream disponible → ignorar ───────────────────────
            lower.contains("afa play") || lower.contains("afaplay")
                || lower == "max" || lower.startsWith("max ")
                || lower.contains("dazn") || lower.contains("mola tv")
                || lower.contains("sky sports") || lower.contains("bt sport")
                || lower.contains("bein") || lower.contains("canal 26")
                || lower.contains("telefe") || lower.contains("el trece")
                || lower.contains("america tv") || lower.contains("canal 9")
                || lower.contains("canal 13") || lower.contains("c5n")
                || lower.contains("tn ") || lower == "tn" -> null

            // ── TyC Sports ───────────────────────────────────────────────────
            lower.contains("tyc") ->
                ChannelRegistry.channels.find { it.id == "tyc-sports" }

            // ── ESPN Premium / Fox Sports Premium (renombrado a ESPN Premium) ─
            lower.contains("espn premium") || lower.contains("espnpremium")
                || lower.contains("fox sports premium") || lower.contains("fox premium") ->
                ChannelRegistry.channels.find { it.id == "espn-premium" }

            // ── ESPN (2, 3, Extra, Knockout, +, etc.) ─────────────────────────
            lower.contains("espn") ->
                ChannelRegistry.channels.find { it.id == "espn-1" }

            // ── Disney+ / Star+ (misma plataforma en Argentina) ───────────────
            lower.contains("disney+") || lower.contains("disney plus")
                || lower.contains("star+") || lower.contains("star plus")
                || lower.contains("star+sports") ->
                ChannelRegistry.channels.find { it.id == "disney-plus" }

            // ── Fox Sports (1, 2, 3) ──────────────────────────────────────────
            lower.contains("fox sports") || lower.contains("fox sport") ->
                ChannelRegistry.channels.find { it.id == "fox-sports" }

            // ── TNT Sports / TNT ─────────────────────────────────────────────
            lower.contains("tnt") ->
                ChannelRegistry.channels.find { it.id == "tnt-sports" }

            // ── DSports+ / DirecTV Sports Online / DirecTV Sports 2 ──────────
            lower.contains("dsports+") || lower.contains("d sports+")
                || lower.contains("directv sports+") || lower.contains("directv sports online")
                || lower.contains("dsports 2") || lower.contains("dsports2")
                || lower.contains("directv +") ->
                ChannelRegistry.channels.find { it.id == "dsports2" }

            // ── DirecTV Sports / DSports ──────────────────────────────────────
            lower.contains("directv") || lower.contains("dsports") ->
                ChannelRegistry.channels.find { it.id == "directv-sports" }

            // ── TV Pública ────────────────────────────────────────────────────
            lower.contains("tv pública") || lower.contains("tv publica")
                || lower.contains("televisión pública") || lower.contains("canal 7") ->
                ChannelRegistry.channels.find { it.id == "tv-publica" }

            // ── DeporTV ───────────────────────────────────────────────────────
            lower.contains("deportv") || lower.contains("depor tv") ->
                ChannelRegistry.channels.find { it.id == "deportv" }

            // ── Fanatiz ───────────────────────────────────────────────────────
            lower.contains("fanatiz") ->
                ChannelRegistry.channels.find { it.id == "fanatiz" }

            // ── Conmebol TV ───────────────────────────────────────────────────
            lower.contains("conmebol") ->
                ChannelRegistry.channels.find { it.id == "conmebol-tv" }

            // ── WIN Sports (Colombia) ─────────────────────────────────────────
            lower.contains("win sports") || lower.contains("winsports")
                || lower.contains("win+") ->
                ChannelRegistry.channels.find { it.id == "win-sports" }

            // ── GolTV ─────────────────────────────────────────────────────────
            lower.contains("goltv") || lower.contains("gol tv") ->
                ChannelRegistry.channels.find { it.id == "goltv" }

            // ── TUDN ─────────────────────────────────────────────────────────
            lower.contains("tudn") ->
                ChannelRegistry.channels.find { it.id == "tudn" }

            else -> null
        }
    }

    private fun detectLeague(text: String): String {
        val t = text.lowercase()
        return when {
            // ── Argentina ───────────────────────────────────────────────────
            t.contains("liga profesional") -> "Liga Profesional Argentina"
            t.contains("copa de la liga") -> "Copa de la Liga Argentina"
            t.contains("copa argentina") -> "Copa Argentina"
            // ── Paraguay ────────────────────────────────────────────────────
            t.contains("copa de primera") -> "Copa de Primera"
            t.contains("apertura") && (t.contains("paraguay") || t.contains("paraguayo")) -> "Apertura Paraguay"
            t.contains("clausura") && (t.contains("paraguay") || t.contains("paraguayo")) -> "Clausura Paraguay"
            // ── Uruguay ─────────────────────────────────────────────────────
            t.contains("primera division") && t.contains("uruguay") -> "Primera División Uruguay"
            t.contains("primera división") && t.contains("uruguay") -> "Primera División Uruguay"
            t.contains("torneo apertura") && t.contains("uruguay") -> "Torneo Apertura Uruguay"
            // ── Colombia ────────────────────────────────────────────────────
            t.contains("betplay") || t.contains("liga colombiana") -> "Liga BetPlay"
            // ── Copas continentales ─────────────────────────────────────────
            t.contains("libertadores") -> "Copa Libertadores"
            t.contains("sudamericana") -> "Copa Sudamericana"
            // ── Europa ──────────────────────────────────────────────────────
            t.contains("champions") -> "UEFA Champions League"
            t.contains("europa league") -> "Europa League"
            t.contains("conference league") -> "Conference League"
            t.contains("premier league") -> "Premier League"
            t.contains("serie a") -> "Serie A"
            t.contains("la liga") -> "La Liga"
            t.contains("bundesliga") -> "Bundesliga"
            t.contains("ligue 1") -> "Ligue 1"
            // ── Selecciones ─────────────────────────────────────────────────
            t.contains("eliminatoria") -> "Eliminatorias"
            t.contains("copa america") || t.contains("copa américa") -> "Copa America"
            t.contains("mundial") || t.contains("world cup") -> "World Cup"
            // ── Otros deportes ───────────────────────────────────────────────
            t.contains("nba") -> "NBA"
            t.contains("nfl") -> "NFL"
            t.contains("formula 1") || t.contains("fórmula 1") || t.contains("f1") -> "Formula 1"
            t.contains("moto gp") || t.contains("motogp") -> "Moto GP"
            t.contains("primera división") || t.contains("primera division") -> "Primera División"
            else -> "Fútbol"
        }
    }
}
