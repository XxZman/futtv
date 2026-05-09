package com.futtv.app.data.api

import android.util.Log
import com.futtv.app.data.model.ChannelRegistry
import com.futtv.app.data.model.EventStatus
import com.futtv.app.data.model.SportEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class TheSportsDbApi(private val client: OkHttpClient) {

    // nombre_clave → URL escudo (persiste toda la sesión)
    private val teamBadgeCache = mutableMapOf<String, String>()

    companion object {
        private const val TAG = "TheSportsDbApi"
        private const val BASE_URL = "https://www.thesportsdb.com/api/v1/json/3"

        private fun teamBadgeUrl(id: String): String =
            if (id.isNotBlank() && id != "null" && id != "0")
                "https://www.thesportsdb.com/images/media/team/badge/$id.png"
            else ""

        private fun leagueBadgeUrl(id: String): String =
            if (id.isNotBlank() && id != "null" && id != "0")
                "https://www.thesportsdb.com/images/media/league/badge/$id.png"
            else ""

        /**
         * Mapa de aliases: como aparece en la web → nombre completo en TheSportsDB.
         * Clave siempre en minúsculas y sin tildes.
         */
        private val TEAM_ALIASES = mapOf(
            // ── Argentina ─────────────────────────────────────────────────────────
            "river"                to "River Plate",
            "river plate"          to "River Plate",
            "boca"                 to "Boca Juniors",
            "boca juniors"         to "Boca Juniors",
            "racing"               to "Racing Club",
            "racing club"          to "Racing Club",
            "independiente"        to "Independiente",
            "san lorenzo"          to "San Lorenzo",
            "huracan"              to "Huracan",
            "huracán"              to "Huracan",
            "velez"                to "Velez Sarsfield",
            "vélez"                to "Velez Sarsfield",
            "velez sarsfield"      to "Velez Sarsfield",
            "velez sarfield"       to "Velez Sarsfield",
            "estudiantes"          to "Estudiantes",
            "lanus"                to "Lanus",
            "lanús"                to "Lanus",
            "tigre"                to "Tigre",
            "talleres"             to "Talleres",
            "belgrano"             to "Belgrano",
            "colon"                to "Colon",
            "colón"                to "Colon",
            "union"                to "Union Santa Fe",
            "unión"                to "Union Santa Fe",
            "godoy cruz"           to "Godoy Cruz",
            "atletico tucuman"     to "Atletico Tucuman",
            "atlético tucumán"     to "Atletico Tucuman",
            "newells"              to "Newell's Old Boys",
            "newell"               to "Newell's Old Boys",
            "newells old boys"     to "Newell's Old Boys",
            "rosario central"      to "Rosario Central",
            "central"              to "Rosario Central",
            "banfield"             to "Banfield",
            "defensa"              to "Defensa y Justicia",
            "defensa y justicia"   to "Defensa y Justicia",
            "arsenal sarandi"      to "Arsenal Sarandi",
            "arsenal"              to "Arsenal Sarandi",
            "platense"             to "Platense",
            "gimnasia"             to "Gimnasia La Plata",
            "gimnasia la plata"    to "Gimnasia La Plata",
            "instituto"            to "Instituto",
            "sarmiento"            to "Sarmiento",
            "aldosivi"             to "Aldosivi",
            "patronato"            to "Patronato",
            "central cordoba"      to "Central Cordoba",
            "central córdoba"      to "Central Cordoba",
            "barracas central"     to "Barracas Central",
            "riestra"              to "Riestra",
            "san martin"           to "San Martin Tucuman",
            "atlético rafaela"     to "Atletico Rafaela",
            "atletico rafaela"     to "Atletico Rafaela",
            // ── Copa Libertadores / Sudamericana ─────────────────────────────────
            "flamengo"             to "Flamengo",
            "palmeiras"            to "Palmeiras",
            "fluminense"           to "Fluminense",
            "atletico mineiro"     to "Atletico Mineiro",
            "atlético mineiro"     to "Atletico Mineiro",
            "botafogo"             to "Botafogo",
            "gremio"               to "Gremio",
            "grêmio"               to "Gremio",
            "internacional"        to "Internacional Porto Alegre",
            "sao paulo"            to "Sao Paulo",
            "santos"               to "Santos",
            "nacional"             to "Nacional",
            "penarol"              to "Penarol",
            "peñarol"              to "Penarol",
            "colo colo"            to "Colo Colo",
            "universidad de chile" to "Universidad de Chile",
            "liga de quito"        to "Liga de Quito",
            "olimpia"              to "Olimpia",
            "cerro porteño"        to "Cerro Porteno",
            "cerro porteno"        to "Cerro Porteno",
            "libertad"             to "Club Libertad",
            "junior"               to "Junior",
            "america de cali"      to "America de Cali",
            "once caldas"          to "Once Caldas",
            // ── Europa ────────────────────────────────────────────────────────────
            "man city"             to "Manchester City",
            "manchester city"      to "Manchester City",
            "man united"           to "Manchester United",
            "man utd"              to "Manchester United",
            "manchester united"    to "Manchester United",
            "chelsea"              to "Chelsea",
            "arsenal fc"           to "Arsenal",
            "liverpool"            to "Liverpool",
            "tottenham"            to "Tottenham Hotspur",
            "spurs"                to "Tottenham Hotspur",
            "newcastle"            to "Newcastle United",
            "west ham"             to "West Ham United",
            "aston villa"          to "Aston Villa",
            "psg"                  to "Paris Saint-Germain",
            "paris sg"             to "Paris Saint-Germain",
            "paris saint-germain"  to "Paris Saint-Germain",
            "lyon"                 to "Olympique Lyonnais",
            "marseille"            to "Olympique Marseille",
            "monaco"               to "AS Monaco",
            "nice"                 to "OGC Nice",
            "inter"                to "Inter Milan",
            "inter milan"          to "Inter Milan",
            "milan"                to "AC Milan",
            "ac milan"             to "AC Milan",
            "juventus"             to "Juventus",
            "juve"                 to "Juventus",
            "napoli"               to "Napoli",
            "roma"                 to "AS Roma",
            "lazio"                to "Lazio",
            "atalanta"             to "Atalanta",
            "fiorentina"           to "Fiorentina",
            "real madrid"          to "Real Madrid",
            "barcelona"            to "FC Barcelona",
            "barca"                to "FC Barcelona",
            "atletico madrid"      to "Atletico Madrid",
            "atlético madrid"      to "Atletico Madrid",
            "atletico"             to "Atletico Madrid",
            "sevilla"              to "Sevilla",
            "valencia"             to "Valencia",
            "villarreal"           to "Villarreal",
            "betis"                to "Real Betis",
            "sociedad"             to "Real Sociedad",
            "bilbao"               to "Athletic Club",
            "athletic"             to "Athletic Club",
            "dortmund"             to "Borussia Dortmund",
            "bvb"                  to "Borussia Dortmund",
            "bayern"               to "Bayern Munich",
            "leipzig"              to "RB Leipzig",
            "frankfurt"            to "Eintracht Frankfurt",
            "leverkusen"           to "Bayer Leverkusen",
            "ajax"                 to "AFC Ajax",
            "porto"                to "FC Porto",
            "benfica"              to "SL Benfica",
            "sporting"             to "Sporting CP",
        )

        /** Quita tildes y caracteres especiales para normalizar nombres */
        fun normalize(s: String): String = s.lowercase()
            .replace(Regex("[áàäâã]"), "a")
            .replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i")
            .replace(Regex("[óòöôõ]"), "o")
            .replace(Regex("[úùüû]"), "u")
            .replace(Regex("[ñ]"), "n")
            .replace(Regex("[ç]"), "c")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    // ─── Eventos del día ─────────────────────────────────────────────────────────

    fun fetchTodayEvents(): List<SportEvent> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val today = dateFormat.format(Date())

        val sports = listOf("Soccer", "Basketball", "American_Football", "Rugby", "Tennis")
        val allEvents = mutableListOf<SportEvent>()

        for (sport in sports) {
            try {
                allEvents.addAll(fetchEventsForSport(today, sport))
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo cargar $sport: ${e.message}")
            }
        }

        return allEvents.sortedBy { it.dateTimeUtc }
    }

    private fun fetchEventsForSport(date: String, sport: String): List<SportEvent> {
        val url = "$BASE_URL/eventsday.php?d=$date&s=${sport.replace("_", "%20")}"
        val request = Request.Builder().url(url).build()

        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string() ?: return emptyList()
        }

        val json = try { JSONObject(body) } catch (e: Exception) {
            Log.e(TAG, "JSON inválido para $sport: ${e.message}")
            return emptyList()
        }

        val eventsArray = json.optJSONArray("events") ?: return emptyList()
        val events = mutableListOf<SportEvent>()

        for (i in 0 until eventsArray.length()) {
            try {
                events.add(parseEvent(eventsArray.getJSONObject(i), sport))
            } catch (e: Exception) {
                Log.w(TAG, "Error parseando evento $i: ${e.message}")
            }
        }
        return events
    }

    private fun parseEvent(json: JSONObject, sport: String): SportEvent {
        val id      = json.optString("idEvent", System.currentTimeMillis().toString())
        val home    = json.optString("strHomeTeam", "Equipo Local")
        val away    = json.optString("strAwayTeam", "Equipo Visitante")
        val league  = json.optString("strLeague", "Liga")
        val dateStr = json.optString("dateEvent", "")
        val timeStr = json.optString("strTime", "")

        val homeTeamId = json.optString("idHomeTeam", "")
        val awayTeamId = json.optString("idAwayTeam", "")
        val leagueId   = json.optString("idLeague", "")

        val homeTeamBadge = teamBadgeUrl(homeTeamId)
        val awayTeamBadge = teamBadgeUrl(awayTeamId)
        val leagueBadge = leagueBadgeUrl(leagueId).ifBlank {
            json.optString("strLeagueBadge", "").takeIf { it.isNotBlank() && it != "null" } ?: ""
        }

        val thumbUrl = listOf(
            json.optString("strThumb", ""),
            json.optString("strBanner", ""),
            json.optString("strFanart1", ""),
            json.optString("strPoster", "")
        ).firstOrNull { it.isNotBlank() && it != "null" } ?: ""

        val strStatus = json.optString("strStatus", "").lowercase().trim()
        val status = when {
            strStatus in listOf("1h", "ht", "2h", "et", "bt", "pen", "live") ||
            strStatus.contains("progress") || strStatus.contains("vivo") -> EventStatus.LIVE

            strStatus in listOf("ft", "aet", "finished", "final") ||
            strStatus.contains("finish") || strStatus.contains("final") -> EventStatus.FINISHED

            else -> EventStatus.UPCOMING
        }

        val homeScore = json.optString("intHomeScore", "").toIntOrNull()
        val awayScore = json.optString("intAwayScore", "").toIntOrNull()

        val dateTimeUtc = if (timeStr.isNotBlank()) "${dateStr}T${timeStr}" else dateStr
        val channels = ChannelRegistry.getChannelsForLeague(league)

        return SportEvent(
            id = id,
            homeTeam = home,
            awayTeam = away,
            league = league,
            sport = sport.replace("_", " "),
            dateTimeUtc = dateTimeUtc,
            thumbUrl = thumbUrl,
            homeTeamBadgeUrl = homeTeamBadge,
            awayTeamBadgeUrl = awayTeamBadge,
            leagueBadgeUrl = leagueBadge,
            status = status,
            homeScore = homeScore,
            awayScore = awayScore,
            channels = channels
        )
    }

    // ─── Búsqueda de escudo por nombre ───────────────────────────────────────────

    /**
     * Busca el escudo de un equipo con múltiples intentos:
     *  1. Nombre original
     *  2. Alias del mapa (ej: "boca" → "Boca Juniors")
     *  3. Nombre normalizado sin tildes
     *  4. Sin prefijos comunes (Club, Deportivo, FC, CA, etc.)
     *  5. Primera palabra significativa (≥4 chars)
     *
     * Todos los resultados se cachean para no repetir llamadas en la misma sesión.
     */
    fun fetchTeamBadge(teamName: String): String {
        val cacheKey = normalize(teamName)
        teamBadgeCache[cacheKey]?.let { return it }

        val candidates = buildSearchCandidates(teamName)
        for (candidate in candidates) {
            val url = searchTeamBadgeUrl(candidate)
            if (url.isNotBlank()) {
                teamBadgeCache[cacheKey] = url
                Log.d(TAG, "Badge found for '$teamName' via '$candidate'")
                return url
            }
        }

        teamBadgeCache[cacheKey] = ""
        Log.d(TAG, "No badge found for '$teamName'")
        return ""
    }

    private fun buildSearchCandidates(teamName: String): List<String> {
        val result = mutableListOf<String>()

        // 1. Nombre original
        result.add(teamName.trim())

        // 2. Alias del mapa
        val key = normalize(teamName)
        TEAM_ALIASES[key]?.let { if (it !in result) result.add(it) }

        // 3. Sin tildes (si difiere del original)
        val noAccents = normalize(teamName).replace(Regex("[^a-z0-9 ]"), " ").trim()
            .split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        if (noAccents != teamName && noAccents.isNotBlank()) result.add(noAccents)

        // 4. Sin prefijos organizativos comunes
        val stripped = teamName.trim()
            .replace(Regex("(?i)^(club |asociación |asociacion |deportivo |atlético |atletico |athletic |fc |cf |ac |as |cd |sd |ud |ca |cs |rj |sc )"), "")
            .trim()
        if (stripped != teamName && stripped.isNotBlank()) result.add(stripped)

        // 5. Primera palabra con ≥4 caracteres (útil para "River", "Boca", "Inter")
        val firstWord = teamName.trim().split(" ").firstOrNull { it.length >= 4 }
        if (firstWord != null && firstWord != teamName) result.add(firstWord)

        return result.distinct()
    }

    private fun searchTeamBadgeUrl(name: String): String {
        return try {
            val encoded = name.trim().replace(" ", "%20")
            val url = "$BASE_URL/searchteams.php?t=$encoded"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                response.body?.string() ?: return ""
            }
            val json = JSONObject(body)
            val teams = json.optJSONArray("teams") ?: return ""
            if (teams.length() == 0) return ""
            val badge = teams.getJSONObject(0).optString("strTeamBadge", "")
            if (badge.isNotBlank() && badge != "null") badge else ""
        } catch (e: Exception) {
            ""
        }
    }

    // ─── Pre-carga de equipos por liga ───────────────────────────────────────────

    /**
     * Carga todos los equipos de una liga y guarda sus escudos en caché.
     * Esto permite resolver escudos de ligas completas con UNA sola llamada API
     * en vez de N llamadas individuales por equipo.
     *
     * IDs de ligas en TheSportsDB:
     *  4406 = Liga Profesional Argentina
     *  4411 = Copa Libertadores
     *  4480 = UEFA Champions League
     *  4328 = Premier League
     *  4335 = La Liga
     */
    fun prefetchLeagueTeams(leagueId: String) {
        try {
            val url = "$BASE_URL/lookup_all_teams.php?id=$leagueId"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                response.body?.string() ?: return
            }
            val json = JSONObject(body)
            val teams = json.optJSONArray("teams") ?: return
            for (i in 0 until teams.length()) {
                val team = teams.getJSONObject(i)
                val name = team.optString("strTeam", "").lowercase().trim()
                val badge = team.optString("strTeamBadge", "")
                if (name.isNotBlank() && badge.isNotBlank() && badge != "null") {
                    teamBadgeCache[normalize(name)] = badge
                }
            }
            Log.d(TAG, "Prefetched ${teams.length()} teams for league $leagueId")
        } catch (e: Exception) {
            Log.w(TAG, "prefetchLeagueTeams($leagueId): ${e.message}")
        }
    }

    fun fetchLocalTime(utcTime: String): String {
        return try {
            val timePart = utcTime.substringAfter("T").take(5)
            if (timePart.matches(Regex("\\d{2}:\\d{2}"))) timePart else utcTime.take(5)
        } catch (e: Exception) {
            utcTime.take(5)
        }
    }
}
