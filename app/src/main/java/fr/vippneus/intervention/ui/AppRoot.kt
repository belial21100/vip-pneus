package fr.vippneus.intervention.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppRoot(vm: AppViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()

    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    Box(Modifier.fillMaxSize()) {
        when (val screen = vm.backStack.last()) {
            Screen.Home -> HomeScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            is Screen.Fps -> FpsFormScreen(vm, screen.id)
            is Screen.Document -> DocumentScreen(vm, screen.id)
            is Screen.Editor -> EditorScreen(vm, screen.id)
            is Screen.Viewer -> ViewerScreen(vm, screen.id)
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
        busy?.let { BusyOverlay(it) }
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.resolvePendingImport(AppViewModel.ImportChoice.ANNULER) },
            title = { Text("Document non reconnu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Ce document n'est pas un modèle connu : les informations ne peuvent pas être reprises " +
                            "automatiquement. Que voulez-vous faire ?"
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.resolvePendingImport(AppViewModel.ImportChoice.FICHE) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Remplir une fiche FPS (document joint)") }
                    OutlinedButton(
                        onClick = { vm.resolvePendingImport(AppViewModel.ImportChoice.DOCUMENT) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Écrire directement sur ce document") }
                    TextButton(onClick = { vm.resolvePendingImport(AppViewModel.ImportChoice.ANNULER) }) { Text("Annuler") }
                }
            },
        )
    }
}
