package com.futtv.app.data.repository

import android.util.Log
import com.futtv.app.data.api.TheSportsDbApi
import com.futtv.app.data.model.Channel
import com.futtv.app.data.model.ChannelRegistry
import com.futtv.app.data.model.SportEvent
import com.futtv.app.data.scraper.AgendaScraper
import com.futtv.app.data.scraper.PelotaLibreScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class SportsRepository(httpClient: OkHttpClient) {

    companion object {
        private const val TAG = "SportsRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes
    }

    private val scraper = PelotaLibreScraper(httpClient)
    private val agendaScraper = AgendaScraper(httpClient)
    private val sportsDbApi = TheSportsDbApi(httpClient)

    private var cachedChannels: List<Channel> = emptyList()
    private var cacheTimestamp: Long = 0L

    private var cachedEvents: List<SportEvent> = emptyList()
    private var eventsCacheTimestamp: Long = 0L

    suspend fun getChannels(forceRefresh: Boolean = false): List<Channel> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedChannels.isNotEmpty() && (now - cacheTimestamp) < CACHE_DURATION_MS) {
            return cachedChannels
        }

        return withContext(Dispatchers.IO) {
            try {
                val channels = scraper.scrapeChannels()
                cachedChannels = channels
                cacheTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Refreshed ${channels.size} channels")
                channels
            } catch (e: Exception) {
                Log.e(TAG, "Error loading channels: ${e.message}")
                if (cachedChannels.isNotEmpty()) cachedChannels
                else throw e
            }
        }
    }

    suspend fun getTodayEvents(forceRefresh: Boolean = false): List<SportEvent> {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedEvents.isNotEmpty() && (now - eventsCacheTimestamp) < CACHE_DURATION_MS) {
            return cachedEvents
        }

        return withContext(Dispatchers.IO) {
            try {
                // Try agenda scraper first — has correct channels per match
                val agendaEvents = agendaScraper.scrapeAgenda()

                if (agendaEvents.isNotEmpty()) {
                    // Enrich with TheSportsDB badges where possible
                    val sportsDbEvents = try { sportsDbApi.fetchTodayEvents() } catch (e: Exception) { emptyList() }

                    val enriched = agendaEvents.map { agendaEvent ->
                        val match = sportsDbEvents.firstOrNull { dbEvent ->
                            namesMatch(dbEvent.homeTeam, agendaEvent.homeTeam) ||
                            namesMatch(dbEvent.awayTeam, agendaEvent.awayTeam)
                        }
                        if (match != null) {
                            agendaEvent.copy(
                                homeTeamBadgeUrl = match.homeTeamBadgeUrl,
                                awayTeamBadgeUrl = match.awayTeamBadgeUrl,
                                leagueBadgeUrl = match.leagueBadgeUrl,
                                status = match.status,
                                homeScore = match.homeScore,
                                awayScore = match.awayScore,
                                channels = agendaEvent.channels.ifEmpty {
                                    ChannelRegistry.getChannelsForLeague(agendaEvent.league).ifEmpty { match.channels }
                                }
                            )
                        } else agendaEvent
                    }

                    // Para eventos que siguen sin escudo (ligas argentinas no aparecen
                    // en el endpoint eventsday de TheSportsDB), buscamos por nombre de equipo
                    val withBadges = enriched.map { event ->
                        if (event.homeTeamBadgeUrl.isBlank() || event.awayTeamBadgeUrl.isBlank()) {
                            val homeBadge = event.homeTeamBadgeUrl.ifBlank {
                                sportsDbApi.fetchTeamBadge(event.homeTeam)
                            }
                            val awayBadge = event.awayTeamBadgeUrl.ifBlank {
                                sportsDbApi.fetchTeamBadge(event.awayTeam)
                            }
                            event.copy(homeTeamBadgeUrl = homeBadge, awayTeamBadgeUrl = awayBadge)
                        } else event
                    }

                    cachedEvents = withBadges
                    eventsCacheTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Using agenda: ${withBadges.size} events")
                    withBadges
                } else {
                    // Fallback to TheSportsDB
                    val events = sportsDbApi.fetchTodayEvents()
                    cachedEvents = events
                    eventsCacheTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Fallback to SportsDB: ${events.size} events")
                    events
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events: ${e.message}")
                if (cachedEvents.isNotEmpty()) cachedEvents else emptyList()
            }
        }
    }

    suspend fun loadAll(): Pair<List<Channel>, List<SportEvent>> = coroutineScope {
        val channelsDeferred = async { getChannels() }
        val eventsDeferred = async { getTodayEvents() }
        Pair(channelsDeferred.await(), eventsDeferred.await())
    }

    fun getLocalTime(utcTime: String): String = sportsDbApi.fetchLocalTime(utcTime)

    /**
     * Compara nombres de equipos normalizando tildes, símbolos y aplicando
     * solapamiento de palabras. Más robusto que take(6) para nombres en español.
     */
    private fun namesMatch(a: String, b: String): Boolean {
        fun normalize(s: String) = s.lowercase()
            .replace(Regex("[áàäâ]"), "a").replace(Regex("[éèëê]"), "e")
            .replace(Regex("[íìïî]"), "i").replace(Regex("[óòöô]"), "o")
            .replace(Regex("[úùüû]"), "u").replace(Regex("[ñ]"), "n")
            .replace(Regex("[^a-z0-9 ]"), "").trim()

        val na = normalize(a)
        val nb = normalize(b)
        if (na == nb) return true
        if (na.contains(nb) || nb.contains(na)) return true
        // Solapamiento de palabras significativas (>2 chars)
        val wordsA = na.split(" ").filter { it.length > 2 }.toSet()
        val wordsB = nb.split(" ").filter { it.length > 2 }.toSet()
        return wordsA.isNotEmpty() && wordsB.isNotEmpty() &&
               wordsA.intersect(wordsB).isNotEmpty()
    }
}
