package fr.vippneus.intervention.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vippneus.intervention.data.ThemeMode

/** Application avec l'apparence choisie dans les réglages (automatique, claire ou sombre). */
@Composable
fun VipApp(vm: AppViewModel, onDarkChange: (Boolean) -> Unit = {}) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dark = when (settings.theme) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.CLAIR -> false
        ThemeMode.SOMBRE -> true
    }
    LaunchedEffect(dark) { onDarkChange(dark) }
    VipTheme(dark) { AppRoot(vm) }
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val result = snackbar.showSnackbar(
                m.text,
                actionLabel = m.action,
                duration = if (m.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.onAction() else m.onDismiss()
        }
    }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    // Garde l'état de chaque écran (défilement, onglet...) pendant qu'on navigue
    val states = rememberSaveableStateHolder()

    BackHandler(enabled = settings.setupDone && vm.backStack.size > 1) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        // Première ouverture : réglages obligatoires avant tout le reste
        Crossfade(targetState = settings.setupDone, label = "configuration") { ready ->
            if (ready) Screens(vm, states) else SetupScreen(vm)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 96.dp),
        ) { data ->
            Snackbar(
                containerColor = Vip.colors.chrome,
                contentColor = Vip.colors.onChrome,
                shape = MaterialTheme.shapes.medium,
                action = data.visuals.actionLabel?.let { label ->
                    {
                        TextButton(onClick = { data.performAction() }) {
                            Text(label, style = MaterialTheme.typography.labelLarge, color = Vip.colors.accent)
                        }
                    }
                },
            ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyLarge) }
        }
        busy?.let { BusyOverlay(it) }
    }
}

/** Écrans de l'application, avec transition et état conservé pendant la navigation. */
@Composable
private fun Screens(vm: AppViewModel, states: SaveableStateHolder) {
    AnimatedContent(
        targetState = vm.backStack.last(),
        transitionSpec = {
            // En avant si l'écran quitté est encore dans la pile
            val forward = initialState in vm.backStack
            val enter = slideInHorizontally(tween(260)) { w -> if (forward) w / 8 else -w / 8 } + fadeIn(tween(220))
            val exit = slideOutHorizontally(tween(260)) { w -> if (forward) -w / 8 else w / 8 } + fadeOut(tween(160))
            enter togetherWith exit
        },
        label = "ecran",
    ) { screen ->
        states.SaveableStateProvider(screen.toString()) {
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                is Screen.Fps -> FpsFormScreen(vm, screen.id)
                is Screen.Document -> DocumentScreen(vm, screen.id)
                is Screen.Editor -> EditorScreen(vm, screen.id)
                is Screen.Viewer -> ViewerScreen(vm, screen.id)
            }
        }
    }
}
