package com.futtv.app.ui.home

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.futtv.app.FutTVApp
import com.futtv.app.data.model.Channel
import com.futtv.app.data.model.ChannelRegistry
import com.futtv.app.data.model.SportEvent
import com.futtv.app.data.model.UiState
import com.futtv.app.updater.AppUpdater
import com.futtv.app.updater.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as FutTVApp).repository

    private val _channelsState = MutableStateFlow<UiState<List<Channel>>>(UiState.Loading)
    val channelsState: StateFlow<UiState<List<Channel>>> = _channelsState

    private val _eventsState = MutableStateFlow<UiState<List<SportEvent>>>(UiState.Loading)
    val eventsState: StateFlow<UiState<List<SportEvent>>> = _eventsState

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel: StateFlow<Channel?> = _selectedChannel

    private val _selectedEvent = MutableStateFlow<SportEvent?>(null)
    val selectedEvent: StateFlow<SportEvent?> = _selectedEvent

    // ─── Update state ─────────────────────────────────────────────────────────
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    // null = sin descarga activa | 0..1 = progreso | -1f = error
    private val _updateProgress = MutableStateFlow<Float?>(null)
    val updateProgress: StateFlow<Float?> = _updateProgress

    // Tiempo (ms) del último refresh exitoso de eventos; 0 = nunca
    private var lastEventsRefreshMs = 0L

    init {
        loadData()
        checkForUpdate()
    }

    /**
     * Llamado cuando la app vuelve al frente (ON_RESUME).
     * Solo refresca si pasaron más de 5 minutos desde el último fetch.
     */
    fun onResume() {
        val now = System.currentTimeMillis()
        if (lastEventsRefreshMs > 0 && (now - lastEventsRefreshMs) > 5 * 60 * 1000L) {
            loadData(forceEvents = true)
        }
    }

    fun loadData(forceEvents: Boolean = false) {
        viewModelScope.launch {
            _channelsState.value = UiState.Loading
            try {
                val channels = repository.getChannels()
                _channelsState.value = if (channels.isEmpty()) {
                    UiState.Success(ChannelRegistry.channels)
                } else {
                    UiState.Success(channels)
                }
            } catch (e: Exception) {
                _channelsState.value = UiState.Success(ChannelRegistry.channels)
            }
        }

        viewModelScope.launch {
            _eventsState.value = UiState.Loading
            try {
                val events = repository.getTodayEvents(forceRefresh = forceEvents)
                lastEventsRefreshMs = System.currentTimeMillis()
                _eventsState.value = if (events.isEmpty()) {
                    UiState.Error("No hay eventos programados para hoy")
                } else {
                    UiState.Success(events)
                }
            } catch (e: Exception) {
                _eventsState.value = UiState.Error("Sin conexión a eventos (${e.message})")
            }
        }
    }

    private fun checkForUpdate() {
        val app = getApplication<FutTVApp>()
        viewModelScope.launch {
            _updateInfo.value = AppUpdater.checkForUpdate(app, app.httpClient)
        }
    }

    fun downloadAndInstall(context: Context) {
        val info = _updateInfo.value ?: return
        if (_updateProgress.value != null && (_updateProgress.value!! in 0f..1f)) return
        val app = getApplication<FutTVApp>()
        viewModelScope.launch {
            _updateProgress.value = 0f
            try {
                val file = AppUpdater.downloadApk(app, info.apkUrl) { progress ->
                    _updateProgress.value = progress
                }
                _updateProgress.value = null
                AppUpdater.installApk(context, file)
            } catch (_: Exception) {
                _updateProgress.value = -1f
            }
        }
    }

    fun dismissUpdateProgress() {
        _updateProgress.value = null
    }

    fun selectChannel(channel: Channel) {
        _selectedChannel.value = channel
    }

    fun dismissChannelDetail() {
        _selectedChannel.value = null
    }

    fun selectEvent(event: SportEvent) {
        val channels = event.channels.ifEmpty {
            ChannelRegistry.getChannelsForLeague(event.league).ifEmpty { ChannelRegistry.channels }
        }
        if (channels.size <= 1) {
            _selectedChannel.value = channels.firstOrNull() ?: ChannelRegistry.channels.first()
        } else {
            _selectedEvent.value = event
        }
    }

    fun dismissEventDetail() {
        _selectedEvent.value = null
    }

    fun refresh() {
        loadData(forceEvents = true)
    }

    fun getLocalTime(utcTime: String): String = repository.getLocalTime(utcTime)
}
