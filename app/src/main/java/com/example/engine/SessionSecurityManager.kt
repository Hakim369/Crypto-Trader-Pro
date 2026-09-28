package com.example.engine

import com.example.data.model.VenueHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionSecurityManager {

    // Strictly in-memory, ZERO persistence in database or SharedPreferences
    private var ephemeralApiKey: String? = null
    private var ephemeralApiSecret: String? = null

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _venueHealth = MutableStateFlow(VenueHealth())
    val venueHealth: StateFlow<VenueHealth> = _venueHealth.asStateFlow()

    fun startSession(key: String, secret: String) {
        if (key.isNotBlank() && secret.isNotBlank()) {
            ephemeralApiKey = key.trim()
            ephemeralApiSecret = secret.trim()
            _isSessionActive.value = true
        }
    }

    /**
     * Suspension Wipe: Clears all sensitive API keys from RAM immediately
     */
    fun terminateSessionAndWipeRam() {
        ephemeralApiKey = null
        ephemeralApiSecret = null
        _isSessionActive.value = false
    }

    fun hasValidActiveSession(): Boolean {
        return _isSessionActive.value && !ephemeralApiKey.isNullOrBlank()
    }

    fun updateHardwareDiagnostics(
        pingBinance: Long,
        pingCoinGlass: Long,
        isLowPower: Boolean,
        isThrottled: Boolean,
        availableRamMb: Int
    ) {
        val isRamConstrained = availableRamMb < 800
        val isLatencySpike = pingBinance > 120 || pingCoinGlass > 150

        _venueHealth.value = _venueHealth.value.copy(
            binancePingMs = pingBinance,
            coinglassPingMs = pingCoinGlass,
            isLatencySpike = isLatencySpike,
            isLowPowerMode = isLowPower,
            isThermallyThrottled = isThrottled,
            availableRamMb = availableRamMb,
            isRamConstrained = isRamConstrained
        )
    }
}
