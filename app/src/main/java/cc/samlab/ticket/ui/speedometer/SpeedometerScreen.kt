package cc.samlab.ticket.ui.speedometer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.samlab.ticket.R
import cc.samlab.ticket.speedometer.*
import cc.samlab.ticket.ui.history.HistoryStation
import cc.samlab.ticket.ui.home.homeColorScheme
import cc.samlab.ticket.ui.home.projectTitleFont

internal const val SPEED_ROUTE = "/railfan/speedometer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SpeedometerScreen(
    location: String,
    dark: Boolean,
    pureBlack: Boolean,
    embedded: Boolean,
    active: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val context = LocalContext.current
    val uri = Uri.parse(location)
    val page = uri.path.orEmpty().removePrefix(SPEED_ROUTE)
    remember { SpeedometerStore.initialize(context); true }
    var permissionError by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf(false) }
    val stations by produceState(emptyList<HistoryStation>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val values = org.json.JSONArray(context.assets.open("speedometer-lib/stations.json")
                    .bufferedReader().use { it.readText() })
                List(values.length()) { index ->
                    val station = values.getJSONArray(index)
                    HistoryStation(station.getString(0), station.getString(1), station.getString(2))
                }
            }.getOrDefault(emptyList())
        }
    }
    fun start() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionError = true
            return
        }
        if (!context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsError = true
            return
        }
        if (SpeedometerStore.current == null) {
            context.startForegroundService(Intent(context, SpeedometerService::class.java).apply {
                putExtra("train", uri.getQueryParameter("train"))
                putExtra("vehicle", uri.getQueryParameter("vehicle"))
                putExtra("from", uri.getQueryParameter("from"))
                putExtra("to", uri.getQueryParameter("to"))
            })
        }
        onNavigate("$SPEED_ROUTE/live")
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        start()
    }
    val live = page == "/live"
    val view = LocalView.current
    DisposableEffect(active && live) {
        if (active && live) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        } else onDispose { }
    }
    val signalLost = !SpeedometerStore.signal && !SpeedometerStore.paused &&
        SpeedometerStore.current?.points?.isNotEmpty() == true
    val scheme = if (live) speedometerColorScheme(signalLost)
        else homeColorScheme(context, dark, pureBlack)
    MaterialExpressiveTheme(colorScheme = scheme) {
        val colors = MaterialTheme.colorScheme
        Surface(Modifier.fillMaxSize(), color = colors.surfaceContainer) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(if (page == "/track") R.string.speed_track else R.string.railfan_speedometer),
                        fontFamily = projectTitleFont) },
                    navigationIcon = {
                        if (!embedded || page.isNotEmpty()) FilledTonalIconButton(
                            onClick = onBack, modifier = Modifier.padding(start = 8.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back)) }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceContainer),
                )
                when (page) {
                    "/guide" -> SpeedometerGuide {
                        val requested = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (android.os.Build.VERSION.SDK_INT >= 33) requested += Manifest.permission.POST_NOTIFICATIONS
                        permissions.launch(requested.toTypedArray())
                    }
                    "/live" -> SpeedometerLive { onNavigate(SPEED_ROUTE) }
                    "/track" -> SpeedometerTrack(
                        SpeedometerStore.history.firstOrNull { it.id == uri.getQueryParameter("id") },
                    )
                    else -> SpeedometerSetup(stations, onNavigate, active)
                }
            }
        }
        if (permissionError || gpsError) AlertDialog(
            onDismissRequest = { permissionError = false; gpsError = false },
            title = { Text(stringResource(R.string.railfan_speedometer)) },
            text = { Text(stringResource(if (gpsError) R.string.speed_gps_required else R.string.speed_permission)) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(if (gpsError) Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    permissionError = false
                    gpsError = false
                }) { Text(stringResource(R.string.speed_settings)) }
            },
            dismissButton = { TextButton(onClick = { permissionError = false; gpsError = false }) {
                Text(stringResource(R.string.action_cancel))
            } },
        )
    }
}
