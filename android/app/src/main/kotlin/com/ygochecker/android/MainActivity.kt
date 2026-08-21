package com.ygochecker.android

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygochecker.android.BuildConfig
import com.ygochecker.android.update.AppUpdateHost
import com.ygochecker.android.update.AppUpdateViewModel
import com.ygochecker.core.designsystem.DuelEntranceSplash
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.LocalOpenDrawer
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.YgoCheckerTheme
import com.ygochecker.core.domain.AccountLinker
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.feature.decklist.DecksRoute
import com.ygochecker.feature.flow.FlowRoute
import com.ygochecker.feature.home.HomeRoute
import com.ygochecker.feature.overlay.OverlayRoute
import com.ygochecker.feature.profile.ProfileRoute
import com.ygochecker.feature.search.SearchRoute
import com.ygochecker.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Keeps Activity in the ContextWrapper chain for Hilt while serving localized resources. */
private class LocaleAwareContext(
    base: Context,
    configuration: Configuration,
) : ContextWrapper(base) {
    private val localized = base.createConfigurationContext(configuration)
    override fun getResources() = localized.resources
    override fun getAssets() = localized.assets
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var accountLinker: AccountLinker
    @Inject lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent { LocalizedApp() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val safeIntent = intent ?: return
        val data: Uri = safeIntent.data ?: return
        if (data.scheme != "ygochecker" || data.host != "oauth") return
        if (data.lastPathSegment?.lowercase() == "magiclink") {
            supabaseClient.handleDeeplinks(safeIntent)
            return
        }
        accountLinker.handleRedirect(data)
    }
}

@HiltViewModel
class AppShellViewModel @Inject constructor(languagePreference: LanguagePreference) : ViewModel() {
    val language = languagePreference.values.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppLanguage.ENGLISH,
    )
}

@Composable
private fun LocalizedApp(viewModel: AppShellViewModel = hiltViewModel()) {
    val language by viewModel.language.collectAsState()
    LocalizedResources(language) {
        YgoCheckerTheme(darkTheme = isSystemInDarkTheme()) {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            if (showSplash) {
                DuelEntranceSplash(
                    onFinished = { showSplash = false },
                    holdMs = 1200L,
                )
            } else {
                val updateVm: AppUpdateViewModel = hiltViewModel()
                val upToDate = stringResource(DesignR.string.update_up_to_date)
                val disabled = stringResource(DesignR.string.update_disabled)
                val failed = stringResource(DesignR.string.update_check_failed)
                AppShell(
                    onCheckUpdates = {
                        updateVm.checkManual(upToDate, disabled, failed)
                    },
                )
                AppUpdateHost(viewModel = updateVm, checkOnLaunch = true)
            }
        }
    }
}

@Composable
private fun LocalizedResources(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val configuration = remember(baseContext, language) {
        Configuration(baseContext.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language.id))
        }
    }
    val localizedContext = remember(baseContext, configuration) {
        LocaleAwareContext(baseContext, configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

/** Primary tabs — swipeable pager. Profile/Settings/Overlay live in the drawer. */
private val primaryTabs = listOf(
    Destination("search", DesignR.string.nav_search, Icons.Default.Search),
    Destination("decks", DesignR.string.nav_decks, Icons.Default.Style),
    Destination("flow", DesignR.string.nav_flow, Icons.Default.AccountTree),
    Destination("home", DesignR.string.nav_home, Icons.Default.Home),
)

/**
 * Overlay is a power-user MDPRO3 companion (screen capture + OCR) — it doesn't need
 * equal billing with Search/Decks/Flow/Home, so it lives here instead of the bottom bar.
 */
private val drawerDestinations = listOf(
    Destination("profile", DesignR.string.nav_profile, Icons.Default.Person),
    Destination("settings", DesignR.string.nav_settings, Icons.Default.Settings),
    Destination("overlay", DesignR.string.nav_overlay, Icons.Default.Visibility),
)

/**
 * Modern shell:
 * - Bottom bar stays fixed; drawer opens **above** it (does not cover the nav).
 * - Primary destinations use a horizontal pager (swipe between tabs).
 * - Profile / Settings / Overlay stay in the account drawer.
 */
@Composable
private fun AppShell(onCheckUpdates: () -> Unit = {}) {
    var section by rememberSaveable { mutableStateOf("tabs") }
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val pagerState = rememberPagerState(
        initialPage = tabIndex.coerceIn(0, primaryTabs.lastIndex),
        pageCount = { primaryTabs.size },
    )

    LaunchedEffect(tabIndex) {
        if (pagerState.currentPage != tabIndex) {
            pagerState.scrollToPage(tabIndex)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (section == "tabs" && tabIndex != pagerState.settledPage) {
            tabIndex = pagerState.settledPage
        }
    }

    val showBottomBar = section == "tabs"
    val selectedTab = if (section == "tabs") tabIndex else -1

    fun goTabs(index: Int) {
        section = "tabs"
        tabIndex = index.coerceIn(0, primaryTabs.lastIndex)
        scope.launch { drawerState.close() }
    }

    // Drawer destinations (Profile/Settings/Overlay) have no back-stack entry of their own —
    // without this, the system back button exits the app instead of returning to the tabs.
    BackHandler(enabled = section != "tabs") {
        section = "tabs"
    }

    CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
        // Scaffold owns the bottom bar so the drawer cannot paint over it.
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!showBottomBar) return@Scaffold
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 0.dp,
                ) {
                    primaryTabs.forEachIndexed { index, destination ->
                        val label = stringResource(destination.label)
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { goTabs(index) },
                            icon = { Icon(destination.icon, label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            ModalNavigationDrawer(
                drawerState = drawerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column(Modifier.padding(bottom = DuelSpacing.space4)) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(DuelSpacing.space4)) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier.size(56.dp),
                                        contentScale = ContentScale.Fit,
                                    )
                                    Text(
                                        stringResource(DesignR.string.splash_brand),
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = DuelSpacing.space2),
                                    )
                                    Text(
                                        stringResource(DesignR.string.nav_drawer_tagline),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                stringResource(DesignR.string.nav_drawer_account),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    start = DuelSpacing.space4,
                                    end = DuelSpacing.space4,
                                    top = DuelSpacing.space4,
                                    bottom = DuelSpacing.space2,
                                ),
                            )
                            drawerDestinations.forEach { destination ->
                                val label = stringResource(destination.label)
                                val selected = section == destination.route
                                NavigationDrawerItem(
                                    label = {
                                        Text(label, style = MaterialTheme.typography.titleSmall)
                                    },
                                    selected = selected,
                                    onClick = {
                                        scope.launch { drawerState.close() }
                                        section = destination.route
                                    },
                                    icon = { Icon(destination.icon, label) },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                                    colors = NavigationDrawerItemDefaults.colors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) {
                when (section) {
                    "profile" -> ProfileRoute()
                    "settings" -> SettingsRoute(
                        onCheckUpdates = onCheckUpdates,
                        installedVersionName = BuildConfig.VERSION_NAME,
                        installedVersionCode = BuildConfig.VERSION_CODE,
                    )
                    "overlay" -> OverlayRoute()
                    else -> HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        key = { primaryTabs[it].route },
                    ) { page ->
                        when (primaryTabs[page].route) {
                            "search" -> SearchRoute()
                            "decks" -> DecksRoute()
                            "flow" -> FlowRoute()
                            "home" -> HomeRoute(
                                onOpenSearch = { goTabs(0) },
                                onOpenDecks = { goTabs(1) },
                                onOpenFlow = { goTabs(2) },
                            )
                        }
                    }
                }
            }
        }
    }
}
