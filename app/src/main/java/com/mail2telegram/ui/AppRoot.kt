package com.mail2telegram.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mail2telegram.App

private data class Tab(val route: String, val label: String, val icon: ImageVector)

fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
fun requestBatteryExemption(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

@Composable
fun AppRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val context = LocalContext.current
    val tabs = remember {
        listOf(
            Tab("accounts", "Accounts", Icons.Filled.Email),
            Tab("filters", "Filters", Icons.Filled.Search),
            Tab("telegram", "Telegram", Icons.AutoMirrored.Filled.Send),
            Tab("log", "Log", Icons.AutoMirrored.Filled.List),
        )
    }

    var showBatteryPrompt by remember {
        mutableStateOf(!App.instance.settings.batteryPromptShown && !isIgnoringBatteryOptimizations(context))
    }
    if (showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { showBatteryPrompt = false },
            title = { Text("Allow background running") },
            text = {
                Text(
                    "To check your mailboxes every minute, Mail2Telegram must be exempt from battery " +
                        "optimization. Otherwise Android will put it to sleep and emails will be delayed."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    App.instance.settings.batteryPromptShown = true
                    showBatteryPrompt = false
                    requestBatteryExemption(context)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = {
                    App.instance.settings.batteryPromptShown = true
                    showBatteryPrompt = false
                }) { Text("Later") }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "accounts", modifier = Modifier.padding(padding)) {
            composable("accounts") { AccountsScreen(onOpen = { id -> nav.navigate("account/$id") }) }
            composable("filters") { FiltersScreen() }
            composable("telegram") { TelegramScreen() }
            composable("log") { LogScreen() }
            composable(
                "account/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { e ->
                AccountEditorScreen(
                    accountId = e.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
