package com.ygochecker.feature.profile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SettingsGroup
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.designsystem.errorMessage
import com.ygochecker.core.domain.AccountLinkEvent
import com.ygochecker.core.domain.AccountLinker
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.domain.ResolveCardById
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CardCollection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FriendEntry
import com.ygochecker.core.model.LinkedAccountProvider
import com.ygochecker.core.model.ProfileAvatarPresets
import com.ygochecker.core.model.ReplayEntry
import com.ygochecker.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val accountLinker: AccountLinker,
    listDecks: ListDecklists,
    private val resolveById: ResolveCardById,
) : ViewModel() {
    val user = profile.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())
    val friends = profile.observeFriends().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val collections = profile.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val replays = profile.observeReplays().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val decks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val linkEvents = accountLinker.events

    var avatar by mutableStateOf<Card?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var collectionCards by mutableStateOf<Map<Long, List<Card>>>(emptyMap())
        private set

    init {
        viewModelScope.launch {
            profile.observeProfile().collect { p ->
                avatar = p.avatarCardId?.let { resolveById.invoke(it) }
            }
        }
        viewModelScope.launch {
            profile.observeCollections().collect { cols ->
                val map = cols.associate { col ->
                    col.id to col.cardIds.mapNotNull { resolveById.invoke(it) }
                }
                collectionCards = map
            }
        }
        viewModelScope.launch {
            accountLinker.events.collect { event ->
                notice = when (event) {
                    is AccountLinkEvent.Linked -> null
                    is AccountLinkEvent.Failed -> event.errorKey
                    is AccountLinkEvent.NeedsManualConfirm -> "manual:${event.provider.name}"
                }
            }
        }
    }

    fun setUsername(value: String) = viewModelScope.launch { profile.setUsername(value) }
    fun setAvatar(cardId: Int?) = viewModelScope.launch { profile.setAvatarCardId(cardId) }
    fun createCollection(name: String) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            notice = "profile.collection_name_required"
            return@launch
        }
        profile.createCollection(trimmed)
        notice = null
    }
    fun deleteCollection(id: Long) = viewModelScope.launch { profile.deleteCollection(id) }
    fun addFriend(name: String) = viewModelScope.launch {
        when (val r = profile.addFriend(name)) {
            is AppResult.Ok -> notice = null
            is AppResult.Err -> notice = r.error.errorKey
        }
    }
    fun removeFriend(id: Long) = viewModelScope.launch { profile.removeFriend(id) }
    fun linkManual(provider: LinkedAccountProvider, id: String) = viewModelScope.launch {
        when (val r = profile.linkAccount(provider, id)) {
            is AppResult.Ok -> notice = null
            is AppResult.Err -> notice = r.error.errorKey
        }
    }
    fun unlink(provider: LinkedAccountProvider) = viewModelScope.launch { profile.unlinkAccount(provider) }
    fun startOAuth(activity: Activity, provider: LinkedAccountProvider) {
        accountLinker.startLink(activity, provider)
    }
    fun addReplay(title: String, note: String) = viewModelScope.launch {
        when (val r = profile.addReplay(title, note)) {
            is AppResult.Ok -> notice = null
            is AppResult.Err -> notice = r.error.errorKey
        }
    }
    fun removeReplay(id: Long) = viewModelScope.launch { profile.removeReplay(id) }
}

@Composable
fun ProfileRoute(vm: ProfileViewModel = hiltViewModel()) {
    val user by vm.user.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val replays by vm.replays.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var usernameDraft by remember(user.username) { mutableStateOf(user.username) }
    var friendDraft by remember { mutableStateOf("") }
    var friendQuery by remember { mutableStateOf("") }
    var collectionDraft by remember { mutableStateOf("") }
    var replayTitle by remember { mutableStateOf("") }
    var replayNote by remember { mutableStateOf("") }
    var avatarPickerOpen by remember { mutableStateOf(false) }
    var manualProvider by remember { mutableStateOf<LinkedAccountProvider?>(null) }
    var linkDraft by remember { mutableStateOf("") }
    var expandedCollection by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(vm.notice) {
        val raw = vm.notice ?: return@LaunchedEffect
        if (raw.startsWith("manual:")) {
            val name = raw.removePrefix("manual:")
            manualProvider = LinkedAccountProvider.entries.firstOrNull { it.name == name }
            linkDraft = ""
        }
    }

    val filteredFriends = remember(friends, friendQuery) {
        val q = friendQuery.trim()
        if (q.isEmpty()) friends else friends.filter { it.displayName.contains(q, ignoreCase = true) }
    }
    val publicDecks = remember(decks) { decks.filter { it.isPublic } }

    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            stringResource(DesignR.string.profile_title),
            stringResource(DesignR.string.profile_subtitle),
        )
        vm.notice?.takeIf { !it.startsWith("manual:") && it != "ok" }?.let { key ->
            Text(
                errorMessage(key),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = DuelSpacing.space4),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
        ) {
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_identity)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
                    ) {
                        Avatar(vm.avatar, onClick = { avatarPickerOpen = true })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = usernameDraft,
                                onValueChange = { usernameDraft = it },
                                label = { Text(stringResource(DesignR.string.profile_username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FilledTonalButton(onClick = { vm.setUsername(usernameDraft) }) {
                                Text(stringResource(DesignR.string.profile_save_username))
                            }
                        }
                    }
                    TextButton(onClick = { avatarPickerOpen = true }) {
                        Text(stringResource(DesignR.string.profile_pick_avatar))
                    }
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_linked_accounts)) {
                    Text(
                        stringResource(DesignR.string.profile_linked_security_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LinkedAccountProvider.entries.forEach { provider ->
                        val linked = user.linkedAccounts[provider]
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(providerLabel(provider), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (linked != null) {
                                        stringResource(DesignR.string.profile_linked_as, maskId(linked))
                                    } else {
                                        stringResource(DesignR.string.profile_not_linked)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (linked != null) {
                                IconButton(onClick = { vm.unlink(provider) }) {
                                    Icon(Icons.Default.LinkOff, stringResource(DesignR.string.profile_unlink))
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        if (activity != null) {
                                            vm.startOAuth(activity, provider)
                                        } else {
                                            manualProvider = provider
                                            linkDraft = ""
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                                    Text(
                                        stringResource(DesignR.string.profile_link),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_friends)) {
                    OutlinedTextField(
                        value = friendQuery,
                        onValueChange = { friendQuery = it },
                        label = { Text(stringResource(DesignR.string.profile_friend_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = friendDraft,
                            onValueChange = { friendDraft = it },
                            label = { Text(stringResource(DesignR.string.profile_friend_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            vm.addFriend(friendDraft)
                            friendDraft = ""
                        }) {
                            Icon(Icons.Default.PersonAdd, stringResource(DesignR.string.profile_add_friend))
                        }
                    }
                    if (filteredFriends.isEmpty()) {
                        Text(
                            stringResource(DesignR.string.profile_friends_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        filteredFriends.forEach { friend ->
                            FriendRow(friend, onRemove = { vm.removeFriend(friend.id) })
                        }
                    }
                    Text(
                        stringResource(DesignR.string.profile_friend_public_decks),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = DuelSpacing.space2),
                    )
                    Text(
                        stringResource(DesignR.string.profile_friend_public_decks_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Local public decks stand in until cloud friend sync exists.
                    DeckMiniList(publicDecks)
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_decks)) {
                    val privateDecks = decks.filter { !it.isPublic }
                    Text(stringResource(DesignR.string.profile_public_decks), style = MaterialTheme.typography.titleSmall)
                    DeckMiniList(publicDecks)
                    Text(
                        stringResource(DesignR.string.profile_private_decks),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = DuelSpacing.space2),
                    )
                    DeckMiniList(privateDecks)
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_replays)) {
                    Text(
                        stringResource(DesignR.string.profile_replays_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = replayTitle,
                        onValueChange = { replayTitle = it },
                        label = { Text(stringResource(DesignR.string.profile_replay_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = replayNote,
                        onValueChange = { replayNote = it },
                        label = { Text(stringResource(DesignR.string.profile_replay_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FilledTonalButton(onClick = {
                        vm.addReplay(replayTitle, replayNote)
                        replayTitle = ""
                        replayNote = ""
                    }) {
                        Text(stringResource(DesignR.string.profile_add_replay))
                    }
                    if (replays.isEmpty()) {
                        Text(
                            stringResource(DesignR.string.profile_replays_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        replays.forEach { replay: ReplayEntry ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(replay.title, style = MaterialTheme.typography.titleSmall)
                                    if (replay.note.isNotBlank()) {
                                        Text(
                                            replay.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                IconButton(onClick = { vm.removeReplay(replay.id) }) {
                                    Icon(Icons.Default.Delete, null)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SettingsGroup(title = stringResource(DesignR.string.profile_collections)) {
                    Text(stringResource(DesignR.string.profile_collection_templates), style = MaterialTheme.typography.titleSmall)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            DesignR.string.profile_template_utopia,
                            DesignR.string.profile_template_spellcaster,
                            DesignR.string.profile_template_inbox,
                        ).forEach { res ->
                            val label = stringResource(res)
                            AssistChip(
                                onClick = { vm.createCollection(label) },
                                label = { Text(label) },
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = collectionDraft,
                            onValueChange = { collectionDraft = it },
                            label = { Text(stringResource(DesignR.string.profile_collection_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(
                            onClick = {
                                vm.createCollection(collectionDraft)
                                collectionDraft = ""
                            },
                            enabled = collectionDraft.trim().isNotEmpty(),
                        ) {
                            Text(stringResource(DesignR.string.profile_create_collection))
                        }
                    }
                    if (collections.isEmpty()) {
                        Text(
                            stringResource(DesignR.string.profile_collections_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        collections.forEach { col ->
                            CollectionBlock(
                                col = col,
                                cards = vm.collectionCards[col.id].orEmpty(),
                                expanded = expandedCollection == col.id,
                                onToggle = {
                                    expandedCollection = if (expandedCollection == col.id) null else col.id
                                },
                                onDelete = { vm.deleteCollection(col.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (avatarPickerOpen) {
        AlertDialog(
            onDismissRequest = { avatarPickerOpen = false },
            title = { Text(stringResource(DesignR.string.profile_pick_avatar)) },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ProfileAvatarPresets.all, key = { it.cardId }) { entry ->
                        val selected = user.avatarCardId == entry.cardId
                        FilterChip(
                            selected = selected,
                            onClick = {
                                vm.setAvatar(entry.cardId)
                                avatarPickerOpen = false
                            },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data("https://images.ygoprodeck.com/images/cards_cropped/${entry.cardId}.jpg")
                                            .build(),
                                        contentDescription = entry.labelEn,
                                        modifier = Modifier.size(36.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Text(entry.labelEn, style = MaterialTheme.typography.labelMedium)
                                }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton({
                    vm.setAvatar(null)
                    avatarPickerOpen = false
                }) { Text(stringResource(DesignR.string.profile_clear_avatar)) }
            },
            dismissButton = {
                TextButton({ avatarPickerOpen = false }) {
                    Text(stringResource(DesignR.string.action_close))
                }
            },
        )
    }

    manualProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { manualProvider = null },
            icon = { Icon(Icons.Default.Link, null) },
            title = { Text(stringResource(DesignR.string.profile_link_title, providerLabel(provider))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(DesignR.string.profile_link_oauth_hint, providerLabel(provider)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(DesignR.string.profile_link_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = linkDraft,
                        onValueChange = { linkDraft = it },
                        label = { Text(stringResource(DesignR.string.profile_external_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton({
                    vm.linkManual(provider, linkDraft)
                    manualProvider = null
                }) { Text(stringResource(DesignR.string.profile_link)) }
            },
            dismissButton = {
                TextButton({ manualProvider = null }) {
                    Text(stringResource(DesignR.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Avatar(card: Card?, onClick: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(72.dp).clickable(onClick = onClick),
    ) {
        if (card != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(card.imageUrl).build(),
                contentDescription = card.name,
                modifier = Modifier.clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(18.dp))
        }
    }
}

@Composable
private fun FriendRow(friend: FriendEntry, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(friend.displayName, style = MaterialTheme.typography.bodyLarge)
            if (friend.note.isNotBlank()) {
                Text(
                    friend.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, stringResource(DesignR.string.action_close))
        }
    }
}

@Composable
private fun CollectionBlock(
    col: CardCollection,
    cards: List<Card>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(DuelSpacing.space3), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                    Text(col.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(DesignR.string.profile_collection_count, col.cardIds.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onToggle) {
                    Text(stringResource(DesignR.string.profile_collection_expand))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null)
                }
            }
            if (expanded && cards.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cards, key = { it.id }) { card ->
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(card.imageUrl)
                                .build(),
                            contentDescription = card.name,
                            modifier = Modifier.size(width = 56.dp, height = 82.dp).clip(MaterialTheme.shapes.small),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckMiniList(decks: List<Decklist>) {
    if (decks.isEmpty()) {
        Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    decks.take(12).forEach { deck ->
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (deck.isPublic) Icons.Default.Public else Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(deck.name, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun providerLabel(provider: LinkedAccountProvider): String = stringResource(
    when (provider) {
        LinkedAccountProvider.DISCORD -> DesignR.string.profile_provider_discord
        LinkedAccountProvider.GOOGLE -> DesignR.string.profile_provider_google
        LinkedAccountProvider.KONAMI -> DesignR.string.profile_provider_konami
    },
)

private fun maskId(id: String): String =
    if (id.length <= 4) "••••" else id.take(2) + "••••" + id.takeLast(2)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
