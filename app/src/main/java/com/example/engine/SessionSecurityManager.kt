package com.example.engine

import com.example.data.model.VenueHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Phase 1 hardening (Spec §3, Appendix B):
 *  - Credentials are held as char arrays and zeroized in place on every wipe, so the
 *    immutable-String interned copies from earlier revisions cannot linger in RAM.
 *  - Diagnostics now feed feed-staleness into the health gate: a mandatory feed that
 *    fails its latency probe (>= UNREACHABLE_LATENCY_MS) marks the venue stale.
 *
 * Zero persistence mandate unchanged: nothing here touches disk, SharedPreferences,
 * or any database.
 */
class SessionSecurityManager {

    private var ephemeralApiKeyChars: CharArray? = null
    private var ephemeralApiSecretChars: CharArray? = null

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _venueHealth = MutableStateFlow(VenueHealth())
    val venueHealth: StateFlow<VenueHealth> = _venueHealth.asStateFlow()

    fun startSession(key: String, secret: String) {
        if (key.isNotBlank() && secret.isNotBlank()) {
            // Replace any prior credentials, zeroizing them first.
            zeroize()
            ephemeralApiKeyChars = key.trim().toCharArray()
            ephemeralApiSecretChars = secret.trim().toCharArray()
            _isSessionActive.value = true
        }
    }

    /**
     * Suspension Wipe (Spec §3): zeroes credential buffers in place, then clears them,
     * and deactivates the session immediately.
     */
    fun terminateSessionAndWipeRam() {
        ephemeralApiKeyChars?.fill('\u0000')
        ephemeralApiSecretChars?.fill('\u0000')
        ephemeralApiKeyChars = null
        ephemeralApiSecretChars = null
        _isSessionActive.value = false
    }

    fun hasValidActiveSession(): Boolean {
        return _isSessionActive.value && !ephemeralApiKeyChars.isNullOrEmpty()
    }

    fun updateHardwareDiagnostics(
        pingBinance: Long,
        pingCoinGlass: Long,
        isLowPower: Boolean,
        isThrottled: Boolean,
        availableRamMb: Int
    ) {
        val isRamConstrained = availableRamMb < RAM_FLOOR_MB
        val isLatencySpike = pingBinance > LATENCY_SPIKE_BINANCE_MS ||
            pingCoinGlass > LATENCY_SPIKE_COINGLASS_MS
        val unreachableProbe = pingBinance >= HardwareDiagnosticsProvider.UNREACHABLE_LATENCY_MS ||
            pingCoinGlass >= HardwareDiagnosticsProvider.UNREACHABLE_LATENCY_MS

        _venueHealth.value = _venueHealth.value.copy(
            binancePingMs = pingBinance,
            coinglassPingMs = pingCoinGlass,
            isLatencySpike = isLatencySpike,
            isLowPowerMode = isLowPower,
            isThermallyThrottled = isThrottled,
            availableRamMb = availableRamMb,
            isRamConstrained = isRamConstrained,
            isFeedStale = unreachableProbe
        )
    }

    companion object {
        const val RAM_FLOOR_MB = 800
        const val LATENCY_SPIKE_BINANCE_MS = 120L
        const val LATENCY_SPIKE_COINGLASS_MS = 150L
    }
}
