@file:OptIn(ExperimentalMaterial3Api::class)

package app.hdaplan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val TAB_TITLES = listOf("Heute", "Woche", "Auswahl", "Einstellungen")

/** Gerüst der App: Tabs unten, Details und Plan-Auswahl als Bottom Sheets. */
@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf(0) }
    var sheet by remember { mutableStateOf<Occ?>(null) }
    var picker by remember { mutableStateOf<PickerReq?>(null) }
    val snack = remember { SnackbarHostState() }

    val calPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        Model.reload()
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        Model.setNotify(ok)
    }
    val askCalendar: () -> Unit = {
        calPerm.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    val needsNotifPermission: () -> Boolean = {
        Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    // Schalter in den Einstellungen: einschalten (ggf. mit Berechtigungsabfrage)
    val enableNotif: () -> Unit = {
        if (needsNotifPermission()) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            Model.setNotify(true)
        }
    }
    // Nach dem Speichern: nur nachfragen, wenn Benachrichtigungen gewünscht sind
    val askNotifIfWanted: () -> Unit = {
        if (Model.notify && needsNotifPermission()) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Beim Start: Kalender-Zugriff anfragen, falls noch nicht erteilt
    LaunchedEffect(Unit) {
        if (!Model.hasCalPerm) askCalendar()
    }

    val msg = Model.message
    LaunchedEffect(msg) {
        if (msg != null) {
            snack.showSnackbar(msg)
            Model.message = null
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(TAB_TITLES[tab], fontWeight = FontWeight.SemiBold) },
                    actions = {
                        if (tab == 0) {
                            IconButton(onClick = { Model.refreshAll() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
                            }
                        }
                    }
                )
                if (Model.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            NavigationBar {
                val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.CheckCircle, Icons.Default.Settings)
                for (i in icons.indices) {
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icons[i], contentDescription = null) },
                        label = { Text(TAB_TITLES[i]) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab == 2 && Model.dirty) {
                ExtendedFloatingActionButton(onClick = {
                    Model.saveDraft()
                    askNotifIfWanted()
                }) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Speichern & eintragen")
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        when (tab) {
            0 -> TodayScreen(pad, { sheet = it }, { tab = 2 })
            1 -> WeekScreen(pad) { sheet = it }
            2 -> SelectionScreen(pad, askCalendar) { tab = 3 }
            else -> SettingsScreen(
                pad,
                onGrantCalendar = askCalendar,
                onEnableNotif = enableNotif,
                onAddPlan = { picker = PickerReq(null) },
                onReplacePlan = { picker = PickerReq(it) }
            )
        }
    }

    val s = sheet
    if (s != null) OccSheet(s) { sheet = null }
    val pk = picker
    if (pk != null) PlanPickerSheet(pk.replaceId) { picker = null }
}
