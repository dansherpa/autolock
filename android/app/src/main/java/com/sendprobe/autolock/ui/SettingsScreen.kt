package com.sendprobe.autolock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sendprobe.autolock.bluelink.LockCarResult
import com.sendprobe.autolock.bluelink.LockCarService
import com.sendprobe.autolock.model.BluelinkCredentials
import com.sendprobe.autolock.notification.NotificationAccess
import com.sendprobe.autolock.storage.AppSettings
import com.sendprobe.autolock.storage.CredentialsStore
import com.sendprobe.autolock.storage.LogStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onViewLogs: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val logStore = remember { LogStore.get(context) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var credentialsStored by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var notificationAccessEnabled by remember { mutableStateOf(NotificationAccess.isEnabled(context)) }
    var isTestingLock by remember { mutableStateOf(false) }
    var testLockResult by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val stored = CredentialsStore.load(context)
        if (stored != null) {
            username = stored.username
            password = stored.password
            pin = stored.pin
            credentialsStored = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAccessEnabled = NotificationAccess.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Autolock") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SettingsSection(title = "Bluelink Account") {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (credentialsStored) "Credentials are saved, encrypted with a key held in the Android Keystore."
                    else "No credentials saved yet.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            CredentialsStore.save(context, BluelinkCredentials(username, password, pin))
                            credentialsStored = true
                            statusMessage = "Saved."
                            logStore.append("credentials_saved")
                        },
                        enabled = username.isNotEmpty() && password.isNotEmpty() && pin.isNotEmpty()
                    ) { Text("Save to Keystore") }

                    if (credentialsStored) {
                        OutlinedButton(onClick = {
                            CredentialsStore.clear(context)
                            username = ""
                            password = ""
                            pin = ""
                            credentialsStored = false
                            statusMessage = "Cleared."
                            logStore.append("credentials_cleared")
                        }) { Text("Clear") }
                    }
                }
            }

            SettingsSection(title = "Automation") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dry Run")
                    Switch(
                        checked = settings.dryRunEnabled,
                        onCheckedChange = { settings.updateDryRunEnabled(it) }
                    )
                }
                Text(
                    if (settings.dryRunEnabled)
                        "Dry run is ON: triggers will log what they would do instead of calling Bluelink."
                    else
                        "Dry run is OFF: triggers will send real commands to your car.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SettingsSection(title = "Cooldown") {
                Text("${settings.debounceSeconds.toInt()}s between trigger attempts")
                Slider(
                    value = settings.debounceSeconds,
                    onValueChange = { settings.updateDebounceSeconds(it) },
                    valueRange = 30f..600f,
                    steps = ((600 - 30) / 15) - 1
                )
                Text(
                    "Repeated triggers within this window after a trigger are ignored, so a flaky signal doesn't spam lock commands.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            SettingsSection(title = "Notification Access") {
                Text(
                    "Autolock listens for the unlock alert Bluelink already posts as a system " +
                        "notification, instead of polling the car's status. This requires " +
                        "Notification Access, a system permission that lets an app read " +
                        "notifications posted by other apps."
                )
                Text(
                    if (notificationAccessEnabled) "Granted." else "Not granted.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = { NotificationAccess.openSettings(context) }) {
                    Text(if (notificationAccessEnabled) "Open Notification Access Settings" else "Grant Notification Access")
                }
            }

            SettingsSection(title = "Manual Test") {
                Button(
                    onClick = {
                        isTestingLock = true
                        testLockResult = null
                        val dryRun = settings.dryRunEnabled
                        coroutineScope.launch {
                            val result = LockCarService.lockCar(context, dryRun = dryRun)
                            testLockResult = describeResult(result)
                            isTestingLock = false
                        }
                    },
                    enabled = !isTestingLock && credentialsStored
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test Lock")
                        if (isTestingLock) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }
                Text(
                    testLockResult ?: if (settings.dryRunEnabled)
                        "Dry run is on, so this will log a simulated attempt rather than calling Bluelink."
                    else
                        "Dry run is off -- this sends a real lock command to your car.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            OutlinedButton(onClick = onViewLogs, modifier = Modifier.fillMaxWidth()) {
                Text("View Logs")
            }

            statusMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

private fun describeResult(result: LockCarResult): String = when (result) {
    is LockCarResult.DryRun -> "Dry run: logged what would have happened. See View Logs for details."
    is LockCarResult.Success -> "Lock confirmed successful."
    is LockCarResult.ConfirmedFailure -> "Bluelink reported the lock command failed."
    is LockCarResult.SentUnconfirmed -> "Lock command sent, but confirmation was inconclusive. Check the car and View Logs."
    is LockCarResult.SkippedNoCredentials -> "No credentials saved -- add them above first."
    is LockCarResult.Debounced -> "Debounced (cooldown still active)."
    is LockCarResult.Failed -> "Failed: ${result.message}"
}
