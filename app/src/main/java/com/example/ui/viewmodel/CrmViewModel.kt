package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CrmManager
import com.example.data.port.CrmPort
import com.example.domain.rules.CrmAccount
import com.example.domain.rules.CrmAccountType
import com.example.domain.rules.CrmContact
import com.example.domain.rules.CrmContactChannel
import com.example.domain.rules.CrmProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class CrmViewModel(
    port: CrmPort,
    private val manager: CrmManager,
) : ViewModel() {

    val profiles: StateFlow<List<CrmProfile>> = port.observeProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val contacts: StateFlow<List<CrmContact>> = port.observeContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun updateDetails(account: CrmAccount, person: String, role: String, phone: String, email: String, notes: String) =
        viewModelScope.launch { manager.updateDetails(account, person, role, phone, email, notes) }

    fun logContact(account: CrmAccount, channel: CrmContactChannel, note: String, date: LocalDate, next: LocalDate?) =
        viewModelScope.launch { manager.logContact(account, channel, note, date, next) }

    fun setNextContact(account: CrmAccount, date: LocalDate?) =
        viewModelScope.launch { manager.setNextContact(account, date) }

    fun addProspect(type: CrmAccountType, name: String) = viewModelScope.launch { manager.addProspect(type, name) }
    fun deleteContact(id: String) = viewModelScope.launch { manager.deleteContact(id) }
    fun deleteAccountData(accountKey: String) = viewModelScope.launch { manager.deleteAccountData(accountKey) }
    fun deleteAll() = viewModelScope.launch { manager.deleteAll() }

    class Factory(
        private val port: CrmPort,
        private val manager: CrmManager,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CrmViewModel(port, manager) as T
    }
}
