package com.mohdshayan.beatwheel.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mohdshayan.beatwheel.di.ServiceLocator
import com.mohdshayan.beatwheel.review.ReviewPrompter
import com.mohdshayan.beatwheel.ui.components.Glyphs
import com.mohdshayan.beatwheel.ui.components.findActivity
import com.mohdshayan.beatwheel.ui.profiles.ProfileEditorScreen
import com.mohdshayan.beatwheel.ui.profiles.ProfilesScreen
import com.mohdshayan.beatwheel.ui.profiles.TemperamentEditorScreen
import com.mohdshayan.beatwheel.ui.session.SessionDetailScreen
import com.mohdshayan.beatwheel.ui.sessions.SessionsScreen
import com.mohdshayan.beatwheel.ui.settings.SettingsScreen
import com.mohdshayan.beatwheel.ui.stand.StandScreen
import com.mohdshayan.beatwheel.ui.theme.Beatwheel
import com.mohdshayan.beatwheel.ui.tuner.TunerScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data class Tune(val openRecorder: Boolean = false)
@Serializable object Sessions
@Serializable object Profiles
@Serializable object Stand
@Serializable object SettingsRoute
@Serializable data class SessionDetail(val id: Long)
@Serializable data class ProfileEditor(val id: Long)
@Serializable data class TemperamentEditor(val id: Long)

private data class TopLevel(val label: String, val icon: ImageVector, val route: Any, val type: KClass<*>)

private val topLevels = listOf(
    TopLevel("Tune", Glyphs.TuningFork, Tune(), Tune::class),
    TopLevel("Sessions", Icons.AutoMirrored.Outlined.ShowChart, Sessions, Sessions::class),
    TopLevel("Profiles", Icons.Outlined.LibraryMusic, Profiles, Profiles::class),
)

private fun NavDestination?.isTopLevel() = topLevels.any { t -> this?.hierarchy?.any { it.hasRoute(t.type) } == true }

/** Pops only while [entry] is still on top, so a double tap or a late save callback never pops the screen underneath. */
private fun NavHostController.popFrom(entry: NavBackStackEntry) {
    if (currentBackStackEntry?.id == entry.id) popBackStack()
}

private fun NavHostController.goTopLevel(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showNav = destination.isTopLevel()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = Beatwheel.colors

    LaunchedEffect(Unit) {
        ServiceLocator.messages.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }
    // The review prompt is considered on leaving Tune, once the microphone has stopped.
    DisposableEffect(navController) {
        var wasTune = false
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, dest, _ ->
            val isTune = dest.hasRoute(Tune::class) || dest.hasRoute(Stand::class)
            if (wasTune && !isTune) {
                scope.launch {
                    delay(1_500)
                    context.findActivity()?.let { ReviewPrompter.maybePrompt(it) }
                }
            }
            wasTune = isTune
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    // The screen stays on in Stand mode and for as long as a drift recording runs, which only takes readings on screen.
    val recording by ServiceLocator.recorder.state.collectAsStateWithLifecycle()
    val isStand = destination?.hasRoute(Stand::class) == true
    val view = LocalView.current
    DisposableEffect(isStand, recording.active) {
        view.keepScreenOn = isStand || recording.active
        onDispose { view.keepScreenOn = false }
    }

    // Movable, so rotating across the 600dp breakpoint moves the NavHost between the bar and rail layouts
    // instead of rebuilding it, and every screen keeps its saved state (an open sheet, drone settings, scroll).
    val host = remember(navController) {
        movableContentOf { modifier: Modifier ->
            NavHost(navController = navController, startDestination = Tune(), modifier = modifier) {
                composable<Tune> { entry ->
                    val route = entry.toRoute<Tune>()
                    TunerScreen(
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                        onOpenStand = { navController.navigate(Stand) },
                        onManageProfiles = { navController.goTopLevel(Profiles) },
                        onSessionSaved = { id -> navController.navigate(SessionDetail(id)) },
                        openRecorder = route.openRecorder,
                    )
                }
                composable<Stand> { entry -> StandScreen(onClose = { navController.popFrom(entry) }) }
                composable<Sessions> {
                    SessionsScreen(
                        onOpenSession = { navController.navigate(SessionDetail(it)) },
                        onRecordDrift = {
                            navController.navigate(Tune(openRecorder = true)) {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable<SessionDetail> { entry -> SessionDetailScreen(onBack = { navController.popFrom(entry) }) }
                composable<Profiles> {
                    ProfilesScreen(
                        onEditProfile = { navController.navigate(ProfileEditor(it)) },
                        onEditTemperament = { navController.navigate(TemperamentEditor(it)) },
                    )
                }
                composable<ProfileEditor> { entry -> ProfileEditorScreen(onBack = { navController.popFrom(entry) }) }
                composable<TemperamentEditor> { entry -> TemperamentEditorScreen(onBack = { navController.popFrom(entry) }) }
                composable<SettingsRoute> { entry -> SettingsScreen(onBack = { navController.popFrom(entry) }) }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                if (showNav) {
                    NavigationRail(containerColor = colors.stand) {
                        topLevels.forEach { t ->
                            val selected = destination?.hierarchy?.any { it.hasRoute(t.type) } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navController.goTopLevel(t.route) },
                                icon = { Icon(t.icon, contentDescription = null) },
                                label = { Text(t.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = colors.neon,
                                    selectedTextColor = colors.ink,
                                    indicatorColor = colors.case,
                                    unselectedIconColor = colors.graphite,
                                    unselectedTextColor = colors.graphite,
                                ),
                            )
                        }
                    }
                }
                host(
                    Modifier
                        .weight(1f)
                        .then(if (showNav) Modifier.consumeWindowInsets(WindowInsets.systemBars.only(WindowInsetsSides.Start)) else Modifier),
                )
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showNav) {
                        NavigationBar(containerColor = colors.stand, tonalElevation = 0.dp) {
                            topLevels.forEach { t ->
                                val selected = destination?.hierarchy?.any { it.hasRoute(t.type) } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.goTopLevel(t.route) },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.label, style = MaterialTheme.typography.labelMedium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.neon,
                                        selectedTextColor = colors.ink,
                                        indicatorColor = colors.case,
                                        unselectedIconColor = colors.graphite,
                                        unselectedTextColor = colors.graphite,
                                    ),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                host(Modifier.padding(padding).consumeWindowInsets(padding))
            }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (showNav && !wide) 80.dp else 8.dp, start = 12.dp, end = 12.dp),
        ) { data ->
            Snackbar(data, containerColor = colors.ink, contentColor = colors.stand, shape = MaterialTheme.shapes.medium)
        }
    }
}
