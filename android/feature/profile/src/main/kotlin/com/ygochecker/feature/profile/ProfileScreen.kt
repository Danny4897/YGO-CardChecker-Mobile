package com.ygochecker.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ygochecker.core.common.AppResult
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.ProfileEmblem
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.SettingsGroup
import com.ygochecker.core.designsystem.ThemedScreenHeader
import com.ygochecker.core.designsystem.errorMessage
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.domain.ListDecklists
import com.ygochecker.core.domain.ProfileRepository
import com.ygochecker.core.domain.ResolveCardById
import com.ygochecker.core.domain.SocialRepository
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.core.model.Card
import com.ygochecker.core.model.CardCollection
import com.ygochecker.core.model.Decklist
import com.ygochecker.core.model.FriendshipStatus
import com.ygochecker.core.model.ProfileAvatarPresets
import com.ygochecker.core.model.ReplayEntry
import com.ygochecker.core.model.SocialChatMessage
import com.ygochecker.core.model.SocialDmMessage
import com.ygochecker.core.model.SocialPublicDeck
import com.ygochecker.core.model.SocialPublicDeckSummary
import com.ygochecker.core.model.SocialUser
import com.ygochecker.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private sealed interface ProfileNav {
    data object Home : ProfileNav
    data class User(val idOrCode: String) : ProfileNav
    data class Deck(val deckId: String) : ProfileNav
    data class Dm(val peerId: String, val peerName: String) : ProfileNav
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val social: SocialRepository,
    listDecks: ListDecklists,
    private val resolveById: ResolveCardById,
    languages: LanguagePreference,
) : ViewModel() {
    val user = profile.observeProfile().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserProfile())
    val collections = profile.observeCollections().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val replays = profile.observeReplays().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val decks = listDecks.invoke().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val apiUrl = social.observeApiUrl().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    val appLanguage = languages.values.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLanguage.ENGLISH)

    var avatar by mutableStateOf<Card?>(null)
        private set
    var avatarEmblem by mutableStateOf<ProfileAvatarPresets.Style?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var meSocial by mutableStateOf<SocialUser?>(null)
        private set
    var friends by mutableStateOf<List<SocialUser>>(emptyList())
        private set
    var incoming by mutableStateOf<List<SocialUser>>(emptyList())
        private set
    var searchResults by mutableStateOf<List<SocialUser>>(emptyList())
        private set
    var viewedUser by mutableStateOf<SocialUser?>(null)
        private set
    var viewedDecks by mutableStateOf<List<SocialPublicDeckSummary>>(emptyList())
        private set
    var publicDeck by mutableStateOf<SocialPublicDeck?>(null)
        private set
    var deckMessages by mutableStateOf<List<SocialChatMessage>>(emptyList())
        private set
    var dmMessages by mutableStateOf<List<SocialDmMessage>>(emptyList())
        private set
    var collectionCards by mutableStateOf<Map<Long, List<Card>>>(emptyMap())
        private set
    var busy by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            profile.observeProfile().collect { p ->
                val id = p.avatarCardId
                if (ProfileAvatarPresets.isEmblem(id)) {
                    avatar = null
                    avatarEmblem = ProfileAvatarPresets.entryFor(id)?.style
                } else {
                    avatarEmblem = null
                    avatar = id?.let { resolveById.invoke(it) }
                }
            }
        }
        viewModelScope.launch {
            profile.observeCollections().collect { cols ->
                collectionCards = cols.associate { col ->
                    col.id to col.cardIds.mapNotNull { resolveById.invoke(it) }
                }
            }
        }
        viewModelScope.launch {
            apiUrl.collect { url ->
                if (url.isNotBlank()) bootstrap()
            }
        }
    }

    fun bootstrap() = viewModelScope.launch {
        val local = user.value
        when (val r = social.ensureSession(local.username, local.avatarCardId)) {
            is AppResult.Ok -> {
                meSocial = r.value
                notice = null
                refreshFriends()
                syncMyPublicDecks()
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun clearNotice() {
        notice = null
    }

    fun setUsername(value: String) = viewModelScope.launch {
        profile.setUsername(value)
        when (val r = social.updateMe(value.trim(), user.value.avatarCardId)) {
            is AppResult.Ok -> meSocial = r.value
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun setAvatar(cardId: Int?) = viewModelScope.launch {
        profile.setAvatarCardId(cardId)
        when (val r = social.updateMe(user.value.username, cardId)) {
            is AppResult.Ok -> meSocial = r.value
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun searchPeople(query: String) = viewModelScope.launch {
        when (val r = social.searchUsers(query)) {
            is AppResult.Ok -> {
                searchResults = r.value
                notice = null
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun loadUser(idOrCode: String) = viewModelScope.launch {
        busy = true
        when (val r = social.getUser(idOrCode)) {
            is AppResult.Ok -> {
                viewedUser = r.value
                notice = null
                when (val d = social.listPublicDecks(r.value.id)) {
                    is AppResult.Ok -> viewedDecks = d.value
                    is AppResult.Err -> notice = d.error.errorKey
                }
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
        busy = false
    }

    fun requestFriend(userId: String) = viewModelScope.launch {
        when (val r = social.requestFriend(userId)) {
            is AppResult.Ok -> {
                viewedUser = viewedUser?.copy(friendship = r.value)
                refreshFriends()
                loadUser(userId)
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun acceptFriend(userId: String) = viewModelScope.launch {
        when (val r = social.acceptFriend(userId)) {
            is AppResult.Ok -> {
                viewedUser = viewedUser?.copy(friendship = r.value)
                refreshFriends()
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun refreshFriends() = viewModelScope.launch {
        when (val r = social.listFriends()) {
            is AppResult.Ok -> {
                friends = r.value.first
                incoming = r.value.second
            }
            is AppResult.Err -> Unit
        }
    }

    fun loadDeck(deckId: String, langFilter: String?) = viewModelScope.launch {
        busy = true
        when (val r = social.getPublicDeck(deckId)) {
            is AppResult.Ok -> {
                publicDeck = r.value
                notice = null
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
        when (val m = social.listDeckMessages(deckId, langFilter)) {
            is AppResult.Ok -> deckMessages = m.value
            is AppResult.Err -> notice = m.error.errorKey
        }
        busy = false
    }

    fun postDeckMessage(deckId: String, body: String, lang: String) = viewModelScope.launch {
        when (val r = social.postDeckMessage(deckId, body, lang)) {
            is AppResult.Ok -> deckMessages = deckMessages + r.value
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun loadDm(peerId: String) = viewModelScope.launch {
        when (val r = social.listDm(peerId)) {
            is AppResult.Ok -> {
                dmMessages = r.value
                notice = null
            }
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun postDm(peerId: String, body: String) = viewModelScope.launch {
        when (val r = social.postDm(peerId, body)) {
            is AppResult.Ok -> dmMessages = dmMessages + r.value
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

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

    fun addReplay(title: String, note: String) = viewModelScope.launch {
        when (val r = profile.addReplay(title, note)) {
            is AppResult.Ok -> notice = null
            is AppResult.Err -> notice = r.error.errorKey
        }
    }

    fun removeReplay(id: Long) = viewModelScope.launch { profile.removeReplay(id) }

    private suspend fun syncMyPublicDecks() {
        decks.value.filter { it.isPublic }.forEach { deck ->
            social.publishDeck(deck)
        }
    }

    suspend fun resolveCard(id: Int): Card? = resolveById.invoke(id)
}

@Composable
fun ProfileRoute(vm: ProfileViewModel = hiltViewModel()) {
    var nav by remember { mutableStateOf<ProfileNav>(ProfileNav.Home) }
    val notice = vm.notice

    Column(Modifier.fillMaxSize()) {
        when (val dest = nav) {
            ProfileNav.Home -> HomeProfile(
                vm = vm,
                onOpenUser = { nav = ProfileNav.User(it) },
                onOpenDeck = { nav = ProfileNav.Deck(it) },
                onOpenDm = { id, name -> nav = ProfileNav.Dm(id, name) },
            )
            is ProfileNav.User -> UserProfilePane(
                vm = vm,
                idOrCode = dest.idOrCode,
                onBack = { nav = ProfileNav.Home },
                onOpenDeck = { nav = ProfileNav.Deck(it) },
                onOpenDm = { id, name -> nav = ProfileNav.Dm(id, name) },
            )
            is ProfileNav.Deck -> PublicDeckPane(
                vm = vm,
                deckId = dest.deckId,
                onBack = { nav = ProfileNav.Home },
            )
            is ProfileNav.Dm -> DmPane(
                vm = vm,
                peerId = dest.peerId,
                peerName = dest.peerName,
                onBack = { nav = ProfileNav.Home },
            )
        }
        notice?.let { key ->
            Text(
                errorMessage(key),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = DuelSpacing.space4, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun HomeProfile(
    vm: ProfileViewModel,
    onOpenUser: (String) -> Unit,
    onOpenDeck: (String) -> Unit,
    onOpenDm: (String, String) -> Unit,
) {
    val local by vm.user.collectAsStateWithLifecycle()
    val decks by vm.decks.collectAsStateWithLifecycle()
    val apiUrl by vm.apiUrl.collectAsStateWithLifecycle()
    val me = vm.meSocial
    var search by remember { mutableStateOf("") }
    var editOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val publicLocal = remember(decks) { decks.filter { it.isPublic } }

    LaunchedEffect(apiUrl, local.username) {
        if (apiUrl.isNotBlank()) vm.bootstrap()
    }
    LaunchedEffect(me?.id) {
        me?.id?.let { vm.loadUser(it) }
    }

    Column(Modifier.fillMaxSize()) {
        ThemedScreenHeader(
            stringResource(DesignR.string.profile_title),
            stringResource(DesignR.string.profile_social_subtitle),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SocialHero(
                    username = me?.username ?: local.username.ifBlank { stringResource(DesignR.string.profile_username) },
                    friendCode = me?.friendCode,
                    avatar = vm.avatar,
                    emblem = vm.avatarEmblem,
                    isSelf = true,
                    friendship = FriendshipStatus.NONE,
                    onEdit = { editOpen = true },
                    onFriend = {},
                    onAccept = {},
                    onMessage = {},
                    onCopyCode = {
                        me?.friendCode?.let { code ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("friendCode", code))
                        }
                    },
                )
            }
            if (apiUrl.isBlank()) {
                item {
                    Text(
                        stringResource(DesignR.string.profile_social_offline_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text(stringResource(DesignR.string.profile_find_people)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            vm.searchPeople(search)
                            val q = search.trim()
                            if (q.startsWith("YG-", ignoreCase = true) || q.length >= 8) {
                                // Prefer opening profile for codes / ids
                            }
                        }) {
                            Icon(Icons.Default.Search, stringResource(DesignR.string.profile_find_people))
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { vm.searchPeople(search) },
                        enabled = search.trim().length >= 2 && apiUrl.isNotBlank(),
                    ) {
                        Text(stringResource(DesignR.string.profile_search_action))
                    }
                    OutlinedButton(
                        onClick = { onOpenUser(search.trim()) },
                        enabled = search.trim().length >= 2 && apiUrl.isNotBlank(),
                    ) {
                        Text(stringResource(DesignR.string.profile_open_profile))
                    }
                }
            }
            if (vm.searchResults.isNotEmpty()) {
                item {
                    Text(stringResource(DesignR.string.profile_search_results), style = MaterialTheme.typography.titleSmall)
                }
                items(vm.searchResults, key = { it.id }) { u ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().clickable { onOpenUser(u.id) },
                    ) {
                        Row(
                            Modifier.padding(DuelSpacing.space3),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(u.username, style = MaterialTheme.typography.titleSmall)
                                Text(u.friendCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(friendshipLabel(u.friendship), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (vm.incoming.isNotEmpty()) {
                item {
                    Text(stringResource(DesignR.string.profile_friend_requests), style = MaterialTheme.typography.titleSmall)
                }
                items(vm.incoming, key = { "in-${it.id}" }) { u ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(u.username, Modifier.weight(1f))
                        TextButton(onClick = { vm.acceptFriend(u.id) }) {
                            Text(stringResource(DesignR.string.profile_accept_friend))
                        }
                    }
                }
            }
            if (vm.friends.isNotEmpty()) {
                item {
                    Text(stringResource(DesignR.string.profile_friends), style = MaterialTheme.typography.titleSmall)
                }
                items(vm.friends, key = { "f-${it.id}" }) { u ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenUser(u.id) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(u.username)
                            Text(u.friendCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onOpenDm(u.id, u.username) }) {
                            Icon(Icons.Default.Message, stringResource(DesignR.string.profile_message))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(DesignR.string.profile_public_decks),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(vm.viewedDecks.filter { it.ownerId == me?.id }, key = { it.id }) { deck ->
                PublicDeckRow(deck.name) { onOpenDeck(deck.id) }
            }
            if (vm.viewedDecks.none { it.ownerId == me?.id } && publicLocal.isNotEmpty()) {
                items(publicLocal, key = { "local-${it.id}" }) { deck ->
                    PublicDeckRow(deck.name) { }
                }
            }
        }
    }

    if (editOpen) {
        EditProfileSheet(vm = vm, onDismiss = { editOpen = false })
    }
}

@Composable
private fun UserProfilePane(
    vm: ProfileViewModel,
    idOrCode: String,
    onBack: () -> Unit,
    onOpenDeck: (String) -> Unit,
    onOpenDm: (String, String) -> Unit,
) {
    LaunchedEffect(idOrCode) { vm.loadUser(idOrCode) }
    val u = vm.viewedUser
    val me = vm.meSocial
    val isSelf = u != null && me != null && u.id == me.id

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(stringResource(DesignR.string.profile_title), style = MaterialTheme.typography.titleMedium)
        }
        if (u == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(DesignR.string.profile_loading))
            }
            return
        }
        LazyColumn(
            contentPadding = PaddingValues(DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(DuelSpacing.space3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                SocialHero(
                    username = u.username,
                    friendCode = u.friendCode,
                    avatar = null,
                    emblem = u.avatarCardId?.let { ProfileAvatarPresets.entryFor(it)?.style },
                    isSelf = isSelf,
                    friendship = u.friendship,
                    onEdit = {},
                    onFriend = { vm.requestFriend(u.id) },
                    onAccept = { vm.acceptFriend(u.id) },
                    onMessage = { onOpenDm(u.id, u.username) },
                    onCopyCode = {},
                )
            }
            item {
                Text(
                    stringResource(DesignR.string.profile_public_decks),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(vm.viewedDecks, key = { it.id }) { deck ->
                PublicDeckRow(deck.name) { onOpenDeck(deck.id) }
            }
            if (vm.viewedDecks.isEmpty()) {
                item {
                    Text(
                        "—",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PublicDeckPane(vm: ProfileViewModel, deckId: String, onBack: () -> Unit) {
    val lang by vm.appLanguage.collectAsStateWithLifecycle()
    var langFilter by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(deckId, langFilter) { vm.loadDeck(deckId, langFilter) }
    val deck = vm.publicDeck

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
            Text(deck?.name ?: stringResource(DesignR.string.profile_public_decks), style = MaterialTheme.typography.titleMedium)
        }
        if (deck == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(DesignR.string.profile_loading))
            }
            return
        }
        Column(
            Modifier.weight(1f).padding(horizontal = DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(DesignR.string.profile_deck_by, deck.ownerUsername),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(DesignR.string.profile_deck_cards), style = MaterialTheme.typography.titleSmall)
            Column(
                Modifier
                    .heightIn(max = 180.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                deck.cards.forEach { c ->
                    Text("${c.quantity}× ${c.name.ifBlank { c.cardId.toString() }} (${c.section})")
                }
            }
            Text(stringResource(DesignR.string.profile_deck_chat), style = MaterialTheme.typography.titleSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = langFilter == null,
                    onClick = { langFilter = null },
                    label = { Text(stringResource(DesignR.string.profile_chat_all_langs)) },
                )
                FilterChip(
                    selected = langFilter == "it",
                    onClick = { langFilter = "it" },
                    label = { Text("IT") },
                )
                FilterChip(
                    selected = langFilter == "en",
                    onClick = { langFilter = "en" },
                    label = { Text("EN") },
                )
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.deckMessages, key = { it.id }) { msg ->
                    Column {
                        Text(
                            "${msg.username} · ${msg.lang}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(DesignR.string.profile_chat_hint)) },
                    singleLine = true,
                )
                IconButton(
                    onClick = {
                        val body = draft.trim()
                        if (body.isNotEmpty()) {
                            val postLang = langFilter ?: lang.id.lowercase().take(2)
                            vm.postDeckMessage(deckId, body, postLang)
                            draft = ""
                        }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, null)
                }
            }
        }
    }
}

@Composable
private fun DmPane(vm: ProfileViewModel, peerId: String, peerName: String, onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(peerId) { vm.loadDm(peerId) }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
            }
            Text(peerName, style = MaterialTheme.typography.titleMedium)
        }
        LazyColumn(
            Modifier.weight(1f).padding(DuelSpacing.space4),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(vm.dmMessages, key = { it.id }) { msg ->
                val mine = msg.fromUserId == vm.meSocial?.id
                Text(
                    msg.body,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = if (mine) TextAlign.End else TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            IconButton(onClick = {
                val body = draft.trim()
                if (body.isNotEmpty()) {
                    vm.postDm(peerId, body)
                    draft = ""
                }
            }) {
                Icon(Icons.AutoMirrored.Filled.Send, null)
            }
        }
    }
}

@Composable
private fun SocialHero(
    username: String,
    friendCode: String?,
    avatar: Card?,
    emblem: ProfileAvatarPresets.Style?,
    isSelf: Boolean,
    friendship: FriendshipStatus,
    onEdit: () -> Unit,
    onFriend: () -> Unit,
    onAccept: () -> Unit,
    onMessage: () -> Unit,
    onCopyCode: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DuelSpacing.space2),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AvatarDisplay(card = avatar, emblem = emblem, onClick = if (isSelf) onEdit else ({}))
        Text(username, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        if (!friendCode.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(friendCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isSelf) {
                    IconButton(onClick = onCopyCode) {
                        Icon(Icons.Default.ContentCopy, stringResource(DesignR.string.profile_copy_code))
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelf) {
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(DesignR.string.profile_edit))
                }
            } else {
                when (friendship) {
                    FriendshipStatus.NONE -> FilledTonalButton(onClick = onFriend) {
                        Icon(Icons.Default.PersonAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(DesignR.string.profile_add_friend))
                    }
                    FriendshipStatus.PENDING_OUT -> OutlinedButton(onClick = {}) {
                        Text(stringResource(DesignR.string.profile_friend_pending))
                    }
                    FriendshipStatus.PENDING_IN -> FilledTonalButton(onClick = onAccept) {
                        Text(stringResource(DesignR.string.profile_accept_friend))
                    }
                    FriendshipStatus.FRIENDS -> OutlinedButton(onClick = {}) {
                        Text(stringResource(DesignR.string.profile_already_friends))
                    }
                }
                if (friendship == FriendshipStatus.FRIENDS) {
                    FilledTonalButton(onClick = onMessage) {
                        Icon(Icons.Default.Message, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(DesignR.string.profile_message))
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicDeckRow(name: String, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(DuelSpacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Public, null, Modifier.size(18.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun EditProfileSheet(vm: ProfileViewModel, onDismiss: () -> Unit) {
    val user by vm.user.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val replays by vm.replays.collectAsStateWithLifecycle()
    var usernameDraft by remember(user.username) { mutableStateOf(user.username) }
    var avatarPickerOpen by remember { mutableStateOf(false) }
    var collectionDraft by remember { mutableStateOf("") }
    var replayTitle by remember { mutableStateOf("") }
    var replayNote by remember { mutableStateOf("") }
    var expandedCollection by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(DesignR.string.profile_edit)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                TextButton(onClick = { avatarPickerOpen = true }) {
                    Text(stringResource(DesignR.string.profile_pick_avatar))
                }
                SettingsGroup(title = stringResource(DesignR.string.profile_collections)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = collectionDraft,
                            onValueChange = { collectionDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(stringResource(DesignR.string.profile_collection_name)) },
                        )
                        FilledTonalButton(onClick = {
                            vm.createCollection(collectionDraft)
                            collectionDraft = ""
                        }) { Text(stringResource(DesignR.string.profile_create_collection)) }
                    }
                    collections.forEach { col ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(col.name, Modifier.weight(1f))
                            IconButton(onClick = { vm.deleteCollection(col.id) }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }
                SettingsGroup(title = stringResource(DesignR.string.profile_replays)) {
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
                    }) { Text(stringResource(DesignR.string.profile_add_replay)) }
                    replays.forEach { replay: ReplayEntry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(replay.title, Modifier.weight(1f))
                            IconButton(onClick = { vm.removeReplay(replay.id) }) {
                                Icon(Icons.Default.Delete, null)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onDismiss) { Text(stringResource(DesignR.string.action_close)) }
        },
    )

    if (avatarPickerOpen) {
        AlertDialog(
            onDismissRequest = { avatarPickerOpen = false },
            title = { Text(stringResource(DesignR.string.profile_pick_avatar)) },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ProfileAvatarPresets.all, key = { it.id }) { entry ->
                        FilterChip(
                            selected = user.avatarCardId == entry.id,
                            onClick = {
                                vm.setAvatar(entry.id)
                                avatarPickerOpen = false
                            },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ProfileEmblem(entry.style, size = 36.dp)
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
                TextButton({ avatarPickerOpen = false }) { Text(stringResource(DesignR.string.action_close)) }
            },
        )
    }
}

@Composable
private fun AvatarDisplay(
    card: Card?,
    emblem: ProfileAvatarPresets.Style?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(96.dp).clickable(onClick = onClick),
    ) {
        when {
            emblem != null -> ProfileEmblem(emblem, size = 96.dp)
            card != null -> AsyncImage(
                model = ImageRequest.Builder(context).data(card.imageUrl).build(),
                contentDescription = card.name,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, Modifier.size(40.dp))
            }
        }
    }
}

@Composable
private fun friendshipLabel(status: FriendshipStatus): String = when (status) {
    FriendshipStatus.FRIENDS -> stringResource(DesignR.string.profile_already_friends)
    FriendshipStatus.PENDING_OUT -> stringResource(DesignR.string.profile_friend_pending)
    FriendshipStatus.PENDING_IN -> stringResource(DesignR.string.profile_accept_friend)
    FriendshipStatus.NONE -> ""
}
