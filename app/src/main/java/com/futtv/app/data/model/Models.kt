package com.futtv.app.data.model

import androidx.compose.ui.graphics.Color

data class StreamServer(
    val label: String,
    val url: String,
    val index: Int = 0
)

data class Channel(
    val id: String,
    val name: String,
    val slug: String,
    val logoUrl: String = "",
    val colorHex: Long = 0xFF1565C0,
    val servers: List<StreamServer> = emptyList(),
    val sourceUrl: String = ""
) {
    val displayColor: Color get() = Color(colorHex)
}

// Estado del partido — se parsea de strStatus de TheSportsDB
enum class EventStatus { UPCOMING, LIVE, FINISHED }

data class SportEvent(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val league: String,
    val sport: String,
    val dateTimeUtc: String,
    val thumbUrl: String = "",
    val homeTeamBadgeUrl: String = "",
    val awayTeamBadgeUrl: String = "",
    val leagueBadgeUrl: String = "",
    val status: EventStatus = EventStatus.UPCOMING,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val channels: List<Channel> = emptyList(),
    // Links directos extraídos de la página de agenda — uno por canal listado en el partido
    val agendaServers: List<StreamServer> = emptyList()
) {
    val title: String get() = "$homeTeam vs $awayTeam"
}

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

object ChannelRegistry {

    // Genera los 3 reproductores de streamhdx para un canal.
    // El WebView carga esta página, Clappr pide el .m3u8 via XHR,
    // shouldInterceptRequest lo captura y se lo pasa a ExoPlayer.
    private fun evtsServers(stream: String): List<StreamServer> = listOf(
        StreamServer("Reproductor 1", "https://streamhdx.com/live1.php?stream=$stream", 0),
        StreamServer("Reproductor 2", "https://streamhdx.com/live2.php?stream=$stream", 1),
        StreamServer("Reproductor 3", "https://streamhdx.com/live3.php?stream=$stream", 2),
    )

    val channels = listOf(
        // ── Argentina ────────────────────────────────────────────────────────
        Channel(
            id = "tyc-sports", name = "TyC Sports", slug = "tyc-sports",
            colorHex = 0xFF0D47A1,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/tyc-sports-ar.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("tycsports")
        ),
        Channel(
            id = "tv-publica", name = "TV Pública", slug = "tv-publica",
            colorHex = 0xFF006064,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/television-publica-ar.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("tvpublica")
        ),
        Channel(
            id = "deportv", name = "DeporTV", slug = "deportv",
            colorHex = 0xFF37474F,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/deportv-ar.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("deportv")
        ),
        // ── ESPN / Disney ─────────────────────────────────────────────────────
        Channel(
            id = "espn-1", name = "ESPN", slug = "espn-1",
            colorHex = 0xFFE65100,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/espn-ar.png",
            sourceUrl = "https://pelotalibrestv.org/en-vivo/espn1-online",
            servers = evtsServers("espn")
        ),
        Channel(
            id = "espn-premium", name = "ESPN Premium", slug = "espn-premium",
            colorHex = 0xFFBF360C,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/espn-premium-ar.png",
            sourceUrl = "https://pelotalibrestv.org/en-vivo/espn-premium-online/",
            servers = evtsServers("espnpremium")
        ),
        Channel(
            id = "disney-plus", name = "Disney+", slug = "disney-plus",
            colorHex = 0xFF1A237E,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/international/disney-plus-int.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("disney")
        ),
        // ── Fox Sports ────────────────────────────────────────────────────────
        Channel(
            id = "fox-sports", name = "Fox Sports", slug = "fox-sports",
            colorHex = 0xFF4A148C,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/fox-sports-ar.png",
            sourceUrl = "https://pelotalibrestv.org/en-vivo/fox-sports-online/",
            servers = evtsServers("foxsports")
        ),
        // ── TNT / Warner ──────────────────────────────────────────────────────
        Channel(
            id = "tnt-sports", name = "TNT Sports", slug = "tnt-sports",
            colorHex = 0xFFB71C1C,
            logoUrl = "https://raw.githubusercontent.com/tv-logo/tv-logos/main/countries/argentina/tnt-sports-ar.png",
            sourceUrl = "https://pelotalibrestv.org/en-vivo/tnt-sports-online",
            servers = evtsServers("tntsports")
        ),
        // ── DirecTV / DSports ─────────────────────────────────────────────────
        Channel(
            id = "directv-sports", name = "DirecTV Sports", slug = "directv-sports",
            colorHex = 0xFF1B5E20,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/5/57/DirecTV-Sports.png",
            sourceUrl = "https://pelotalibrestv.org/en-vivo/directv-sports-online",
            servers = evtsServers("dsports")
        ),
        Channel(
            id = "dsports2", name = "DSports+", slug = "directv-sports-online",
            colorHex = 0xFF1A237E,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/5/57/DirecTV-Sports.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("dsports2")
        ),
        // ── Fanatiz ────────────────────────────────────────────────────────────
        Channel(
            id = "fanatiz", name = "Fanatiz", slug = "fanatiz",
            colorHex = 0xFF00897B,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/1/1d/Fanatiz_Logo_%282017%E2%80%932020%29.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("fanatiz")
        ),
        // ── Conmebol TV ────────────────────────────────────────────────────────
        Channel(
            id = "conmebol-tv", name = "Conmebol TV", slug = "conmebol-tv",
            colorHex = 0xFF1565C0,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f7/CONMEBOL.svg/320px-CONMEBOL.svg.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("conmebol")
        ),
        // ── WIN Sports (Colombia) ──────────────────────────────────────────────
        Channel(
            id = "win-sports", name = "WIN Sports", slug = "win-sports",
            colorHex = 0xFFE53935,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/39/Win_Sports_nuevo_logo.svg/320px-Win_Sports_nuevo_logo.svg.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("winsports")
        ),
        // ── GolTV ──────────────────────────────────────────────────────────────
        Channel(
            id = "goltv", name = "GolTV", slug = "goltv",
            colorHex = 0xFF558B2F,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/GolTV_logo.svg/320px-GolTV_logo.svg.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("goltv")
        ),
        // ── TUDN ──────────────────────────────────────────────────────────────
        Channel(
            id = "tudn", name = "TUDN", slug = "tudn",
            colorHex = 0xFF880E4F,
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6c/TUDN_Logo.svg/320px-TUDN_Logo.svg.png",
            sourceUrl = "https://pelotalibrestv.org/",
            servers = evtsServers("tudn")
        )
    )

    val leagueToChannels = mapOf(
        // ── Fútbol argentino ─────────────────────────────────────────────────
        "Liga Profesional Argentina" to listOf("tyc-sports", "tnt-sports"),
        "Copa de la Liga Argentina"  to listOf("tyc-sports", "tnt-sports"),
        "Copa Argentina"             to listOf("tyc-sports", "deportv"),
        // ── Fútbol Paraguay ──────────────────────────────────────────────────
        "Copa de Primera"            to listOf("fanatiz"),
        "Apertura Paraguay"          to listOf("fanatiz"),
        "Clausura Paraguay"          to listOf("fanatiz"),
        // ── Fútbol Uruguay ───────────────────────────────────────────────────
        "Primera División Uruguay"   to listOf("disney-plus"),
        "Torneo Apertura Uruguay"    to listOf("disney-plus"),
        // ── Fútbol Colombia ──────────────────────────────────────────────────
        "Liga BetPlay"               to listOf("win-sports"),
        // ── Copas sudamericanas ──────────────────────────────────────────────
        "Copa Libertadores"          to listOf("espn-premium", "conmebol-tv", "tnt-sports"),
        "Copa Sudamericana"          to listOf("espn-premium", "conmebol-tv"),
        // ── Europa ───────────────────────────────────────────────────────────
        "UEFA Champions League"      to listOf("espn-premium", "tnt-sports"),
        "Europa League"              to listOf("espn-premium"),
        "Conference League"          to listOf("espn-premium"),
        "Premier League"             to listOf("espn-premium", "directv-sports"),
        "La Liga"                    to listOf("espn-premium", "directv-sports"),
        "Serie A"                    to listOf("espn-premium", "directv-sports"),
        "Bundesliga"                 to listOf("espn-premium", "directv-sports"),
        "Ligue 1"                    to listOf("espn-premium", "directv-sports"),
        // ── Selecciones ──────────────────────────────────────────────────────
        "Copa America"               to listOf("tv-publica", "tyc-sports", "tnt-sports"),
        "World Cup"                  to listOf("tv-publica", "tyc-sports", "tnt-sports"),
        "Eliminatorias"              to listOf("tyc-sports", "tnt-sports"),
        // ── Otros deportes ───────────────────────────────────────────────────
        "NBA"                        to listOf("espn-1", "espn-premium"),
        "NFL"                        to listOf("espn-1", "directv-sports"),
        "Formula 1"                  to listOf("fox-sports", "espn-premium"),
        "Moto GP"                    to listOf("fox-sports"),
        "MLS"                        to listOf("espn-1"),
        "Tennis"                     to listOf("espn-1", "directv-sports"),
        "Rugby"                      to listOf("espn-1", "directv-sports"),
        "Boxing"                     to listOf("espn-1", "fox-sports"),
    )

    fun getChannelsForLeague(league: String): List<Channel> {
        val ids = leagueToChannels.entries
            .firstOrNull { (k, _) -> league.contains(k, ignoreCase = true) }
            ?.value ?: return emptyList()
        return channels.filter { it.id in ids }
    }
}
