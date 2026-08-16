package com.ygochecker.core.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@StringRes
fun errorStringResource(errorKey: String): Int = when (errorKey) {
    "catalog.unavailable" -> R.string.error_catalog_unavailable
    "network.unavailable" -> R.string.error_network
    "deck.not_found" -> R.string.error_deck_not_found
    "deck.save_failed" -> R.string.error_deck_save_failed
    "deck.storage_failed" -> R.string.error_deck_storage_failed
    "deck.import.ydke_invalid" -> R.string.error_deck_import_ydke_invalid
    "deck.import.ydke_card_not_found" -> R.string.error_deck_import_ydke_card_not_found
    "deck.import.section_invalid" -> R.string.error_deck_import_section_invalid
    "deck.import.line_invalid" -> R.string.error_deck_import_line_invalid
    "deck.import.quantity_invalid" -> R.string.error_deck_import_quantity_invalid
    "deck.import.card_not_found" -> R.string.error_deck_import_card_not_found
    "profile.link_invalid" -> R.string.error_profile_link_invalid
    "profile.friend_invalid" -> R.string.error_profile_friend_invalid
    "profile.link_oauth_denied" -> R.string.error_profile_link_oauth_denied
    "profile.link_oauth_failed" -> R.string.error_profile_link_oauth_failed
    "profile.link_oauth_not_configured" -> R.string.error_profile_link_oauth_not_configured
    "profile.replay_invalid" -> R.string.error_profile_replay_invalid
    "profile.collection_name_required" -> R.string.profile_collection_name_required
    "update.network" -> R.string.error_update_network
    "update.invalid" -> R.string.error_update_invalid
    "update.disabled" -> R.string.update_disabled
    "social.not_configured" -> R.string.error_social_not_configured
    "social.unauthorized" -> R.string.error_social_unauthorized
    "social.network" -> R.string.error_social_network
    "social.user_not_found" -> R.string.error_social_user_not_found
    "social.friend_self" -> R.string.error_social_friend_self
    "social.friend_no_pending" -> R.string.error_social_friend_no_pending
    "social.dm_friends_only" -> R.string.error_social_dm_friends_only
    "social.message_empty" -> R.string.error_social_message_empty
    "social.deck_not_found" -> R.string.error_social_deck_not_found
    "social.username_invalid" -> R.string.error_social_username_invalid
    else -> R.string.error_unknown
}

@Composable
fun errorMessage(errorKey: String, detail: String? = null): String {
    val resource = errorStringResource(errorKey)
    val requiresDetail = errorKey in setOf(
        "deck.import.section_invalid",
        "deck.import.line_invalid",
        "deck.import.quantity_invalid",
        "deck.import.card_not_found",
        "deck.import.ydke_card_not_found",
    )
    return if (requiresDetail) {
        stringResource(resource, detail.orEmpty())
    } else {
        stringResource(resource)
    }
}
