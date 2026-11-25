package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PreparedQuote
import com.example.data.QuoteFunnelManager
import com.example.data.port.QuoteFunnelPort
import com.example.domain.rules.EventQuote
import com.example.domain.rules.SavedQuote
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class QuoteFunnelViewModel(
    port: QuoteFunnelPort,
    private val manager: QuoteFunnelManager,
) : ViewModel() {

    val quotes: StateFlow<List<SavedQuote>> = port.observeQuotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun prepare(existingId: String?, quote: EventQuote, today: LocalDate): PreparedQuote =
        manager.prepare(existingId, quote, today)

    suspend fun save(prepared: PreparedQuote, today: LocalDate, markSent: Boolean): SavedQuote =
        manager.save(prepared, today, markSent)

    fun markSent(id: String, today: LocalDate) = viewModelScope.launch { manager.markSent(id, today) }
    fun scheduleFollowUp(id: String, date: LocalDate) = viewModelScope.launch { manager.scheduleFollowUp(id, date) }
    fun markWon(id: String) = viewModelScope.launch { manager.markWon(id) }
    fun markLost(id: String, reason: String) = viewModelScope.launch { manager.markLost(id, reason) }
    fun reopen(id: String, today: LocalDate) = viewModelScope.launch { manager.reopen(id, today) }
    fun markConverted(id: String) = viewModelScope.launch { manager.markConverted(id) }
    fun delete(id: String) = viewModelScope.launch { manager.delete(id) }
    fun deleteAll() = viewModelScope.launch { manager.deleteAll() }

    class Factory(
        private val port: QuoteFunnelPort,
        private val manager: QuoteFunnelManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QuoteFunnelViewModel(port, manager) as T
    }
}
