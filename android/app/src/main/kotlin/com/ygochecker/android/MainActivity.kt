package com.ygochecker.android

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ygochecker.android.BuildConfig
import com.ygochecker.android.update.AppUpdateHost
import com.ygochecker.android.update.AppUpdateViewModel
import com.ygochecker.core.designsystem.DuelEntranceSplash
import com.ygochecker.core.designsystem.DuelSpacing
import com.ygochecker.core.designsystem.LocalOpenDrawer
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.designsystem.YgoCheckerTheme
import com.ygochecker.core.domain.LanguagePreference
import com.ygochecker.core.model.AppLanguage
import com.ygochecker.feature.decklist.DecksRoute
import com.ygochecker.feature.flow.FlowRoute
import com.ygochecker.feature.overlay.OverlayRoute
import com.ygochecker.core.domain.AccountLinker
import com.ygochecker.feature.profile.ProfileRoute
import com.ygochecker.feature.search.SearchRoute
import com.ygochecker.feature.settings.SettingsRoute
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
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
        val data: Uri = intent?.data ?: return
        if (data.scheme != "ygochecker" || data.host != "oauth") return
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
                    holdMs = 1100L,
                    logo = {
                        Image(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp),
                            contentScale = ContentScale.Fit,
                        )
                    },
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

/** Primary tabs — Settings stays in the drawer. */
private val bottomDestinations = listOf(
    Destination("search", DesignR.string.nav_search, Icons.Default.Search),
    Destination("decks", DesignR.string.nav_decks, Icons.Default.Style),
    Destination("flow", DesignR.string.nav_flow, Icons.Default.AccountTree),
    Destination("overlay", DesignR.string.nav_overlay, Icons.Default.Visibility),
    Destination("profile", DesignR.string.nav_profile, Icons.Default.Person),
)

private val drawerDestinations = listOf(
    Destination("profile", DesignR.string.nav_profile, Icons.Default.Person),
    Destination("settings", DesignR.string.nav_settings, Icons.Default.Settings),
)

@Composable
private fun AppShell(onCheckUpdates: () -> Unit = {}) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    val showBottomBar = route in bottomDestinations.map { it.route }

    CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
        ModalNavigationDrawer(
            drawerState = drawerState,
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
                            val selected = route == destination.route
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                },
                                selected = selected,
                                onClick = {
                                    scope.launch { drawerState.close() }
                                    nav.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    }
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
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!showBottomBar) return@Scaffold
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp,
                    ) {
                        bottomDestinations.forEach { destination ->
                            val label = stringResource(destination.label)
                            val selected = route == destination.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(destination.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    }
                                },
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
                NavHost(
                    navController = nav,
                    startDestination = "search",
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
                    composable("search") { SearchRoute() }
                    composable("decks") { DecksRoute() }
                    composable("overlay") { OverlayRoute() }
                    composable("flow") { FlowRoute() }
                    composable("profile") { ProfileRoute() }
                    composable("settings") {
                        SettingsRoute(
                            onCheckUpdates = onCheckUpdates,
                            installedVersionName = BuildConfig.VERSION_NAME,
                            installedVersionCode = BuildConfig.VERSION_CODE,
                        )
                    }
                }
            }
        }
    }
}
