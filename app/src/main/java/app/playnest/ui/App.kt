package app.playnest.ui

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.playnest.AppVm
import app.playnest.Screen

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.Home, "Home", Icons.Default.Home),
    Tab(Screen.Search, "Search", Icons.Default.Search),
    Tab(Screen.Subs, "Subs", Icons.Default.Subscriptions),
    Tab(Screen.Library, "Library", Icons.Default.VideoLibrary),
)

@Composable
fun App(vm: AppVm, activity: ComponentActivity) {
    val ctl = vm.ctl
    val screen = vm.stack.last()
    val playerOpen = ctl.current != null && ctl.expanded

    // fullscreen = landscape + hidden system bars
    LaunchedEffect(ctl.fullscreen) {
        activity.requestedOrientation =
            if (ctl.fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val c = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (ctl.fullscreen) {
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            c.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = ctl.fullscreen) { ctl.fullscreen = false }
    BackHandler(enabled = !ctl.fullscreen && playerOpen) { ctl.expanded = false }
    BackHandler(enabled = !ctl.fullscreen && !playerOpen && vm.stack.size > 1) { vm.pop() }

    if (ctl.inPip && ctl.current != null) {
        PipVideo(ctl)
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (ctl.current != null && !ctl.expanded) MiniPlayer(ctl)
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = vm.tab == t.screen,
                                onClick = { vm.openTab(t.screen) },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    Screen.Home -> HomeScreen(vm)
                    Screen.Search -> SearchScreen(vm)
                    Screen.Subs -> SubsScreen(vm)
                    Screen.Library -> LibraryScreen(vm)
                    Screen.Settings -> SettingsScreen(vm)
                    is Screen.Channel -> ChannelScreen(vm, screen.url)
                    is Screen.Playlist -> PlaylistScreen(vm, screen.url)
                }
            }
        }
        if (playerOpen) PlayerScreen(vm)
    }
}
