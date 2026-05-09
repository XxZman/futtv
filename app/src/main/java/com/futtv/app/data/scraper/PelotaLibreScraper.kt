package com.futtv.app.data.scraper

import com.futtv.app.data.model.Channel
import com.futtv.app.data.model.ChannelRegistry
import okhttp3.OkHttpClient

class PelotaLibreScraper(private val client: OkHttpClient) {

    /**
     * Devuelve los canales directamente desde ChannelRegistry.
     * Los logos y servidores ya están curados ahí con URLs de alta calidad.
     */
    suspend fun scrapeChannels(): List<Channel> = ChannelRegistry.channels
}
