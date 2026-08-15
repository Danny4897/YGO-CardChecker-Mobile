package com.ygochecker.core.domain

import android.app.Activity
import android.net.Uri
import com.ygochecker.core.model.LinkedAccountProvider
import kotlinx.coroutines.flow.SharedFlow

data class AuthClientConfig(
    val discordClientId: String = "",
    val discordClientSecret: String = "",
    val googleClientId: String = "",
) {
    val discordConfigured: Boolean get() = discordClientId.isNotBlank()
    val googleConfigured: Boolean get() = googleClientId.isNotBlank()
}

sealed interface AccountLinkEvent {
    data class Linked(val provider: LinkedAccountProvider, val displayId: String) : AccountLinkEvent
    data class Failed(val provider: LinkedAccountProvider, val errorKey: String) : AccountLinkEvent
    data class NeedsManualConfirm(val provider: LinkedAccountProvider) : AccountLinkEvent
}

/**
 * Opens Discord / Google on-device OAuth (Custom Tabs / app handoff) and completes
 * linking via deep link `ygochecker://oauth/...`.
 */
interface AccountLinker {
    val events: SharedFlow<AccountLinkEvent>
    fun startLink(activity: Activity, provider: LinkedAccountProvider)
    fun handleRedirect(uri: Uri)
}
