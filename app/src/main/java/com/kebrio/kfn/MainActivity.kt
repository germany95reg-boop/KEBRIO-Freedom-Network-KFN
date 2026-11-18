package com.kebrio.kfn

import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kebrio.kfn.core.WireGuardController
import com.kebrio.kfn.core.ProfileVault
import com.wireguard.config.Config

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val controller = WireGuardController.get(this)
  val vault = ProfileVault(applicationContext)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme()) {
    var network by remember { mutableStateOf(checkNetwork()) }
    var config by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf(setOf<String>()) }
    val apps = remember {
     val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
     packageManager.queryIntentActivities(query, 0)
      .filter { it.activityInfo.packageName != packageName }
      .distinctBy { it.activityInfo.packageName }
      .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
      .sortedBy { it.first.lowercase() }
    }
    var showApps by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var vaultBusy by remember { mutableStateOf(false) }
    var vaultMessage by remember { mutableStateOf("") }
    fun vaultAction(action: suspend () -> String) {
     vaultBusy = true
     scope.launch {
      try { vaultMessage = action() }
      catch (e: Exception) {
       if (e is kotlinx.coroutines.CancellationException) throw e
       vaultMessage = "Profile operation failed"
      } finally { vaultBusy = false }
     }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
     if (uri != null) scope.launch {
      try {
       val raw = withContext(Dispatchers.IO) {
        contentResolver.openInputStream(uri)?.use { input ->
         val output = java.io.ByteArrayOutputStream()
         val buffer = ByteArray(4096)
         while (true) {
          val count = input.read(buffer)
          if (count < 0) break
          require(output.size() + count <= 65536)
          output.write(buffer, 0, count)
         }
         output.toString("UTF-8")
        } ?: error("Cannot open file")
       }
       config = raw
       importError = ""
      } catch (e: Exception) {
       if (e is kotlinx.coroutines.CancellationException) throw e
       importError = "Cannot import configuration (maximum 64 KiB)"
      }
     }
    }
    val status by controller.status.collectAsState()
    val traffic by controller.traffic.collectAsState()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
     if (VpnService.prepare(this@MainActivity) == null && config.isNotBlank()) {
      controller.connect(config, excluded); config = ""
     }
    }
    Surface(Modifier.fillMaxSize()) {
     Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
      Text("KEBRIO", style = MaterialTheme.typography.headlineLarge)
      Text("FREEDOM NETWORK", style = MaterialTheme.typography.labelLarge)
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("WireGuard", style = MaterialTheme.typography.headlineSmall)
        Text(status)
        Text("Tunnel traffic", style = MaterialTheme.typography.titleMedium)
        Text("Received: " + (traffic?.received?.let { android.text.format.Formatter.formatShortFileSize(this@MainActivity, it) } ?: "—"))
        Text("Sent: " + (traffic?.sent?.let { android.text.format.Formatter.formatShortFileSize(this@MainActivity, it) } ?: "—"))
        Text("Import or paste a configuration from your trusted VPN server. Keys are stored only if you explicitly save a profile.")
        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Import .conf") }
        if (importError.isNotEmpty()) Text(importError, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(value = config, onValueChange = { if (it.length <= 65536) config = it }, label = { Text("WireGuard configuration") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, visualTransformation = PasswordVisualTransformation())
        Text("Encrypted local profile")
        OutlinedButton(enabled = !vaultBusy && config.isNotBlank(), onClick = {
         val snapshot = config
         vaultAction {
          withContext(Dispatchers.IO) {
           Config.parse(snapshot.byteInputStream(Charsets.UTF_8))
           vault.save(snapshot)
          }
          "Profile saved securely"
         }
        }) { Text("Save profile") }
        OutlinedButton(enabled = !vaultBusy, onClick = {
         vaultAction {
          val saved = withContext(Dispatchers.IO) { vault.load() }
          if (saved == null) "No saved profile" else { config = saved; "Profile loaded" }
         }
        }) { Text("Load profile") }
        OutlinedButton(enabled = !vaultBusy, onClick = {
         vaultAction {
          withContext(Dispatchers.IO) { vault.delete() }
          config = ""
          "Saved profile deleted"
         }
        }) { Text("Delete saved profile") }
        if (vaultMessage.isNotEmpty()) Text(vaultMessage)
        Button(onClick = {
         val intent = VpnService.prepare(this@MainActivity)
         if (intent == null) { controller.connect(config, excluded); config = "" } else launcher.launch(intent)
        }, enabled = config.isNotBlank() && !status.startsWith("Tunnel up") && status != "Starting tunnel") { Text("Connect") }
        OutlinedButton(onClick = { controller.disconnect() }) { Text("Disconnect") }
       }
      }
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Network diagnostics", style = MaterialTheme.typography.titleLarge)
        Text(network)
        OutlinedButton(onClick = { network = checkNetwork() }) { Text("Refresh") }
       }
      }
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("KFN Shield", style = MaterialTheme.typography.titleLarge)
        Text("Android can block connections outside an always-on VPN. Configure this in system VPN settings if available for KFN.")
        OutlinedButton(onClick = {
         try { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
         catch (e: android.content.ActivityNotFoundException) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }) { Text("Open Android VPN settings") }
        Text("Lockdown status is not verified by KFN.")
       }
      }
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("KFN Split", style = MaterialTheme.typography.titleLarge)
        Text("Selected apps bypass VPN on the next connection. Android lockdown may block their traffic.")
        Text("Excluded: " + excluded.size)
        OutlinedButton(onClick = { showApps = !showApps }) { Text(if (showApps) "Hide apps" else "Choose apps") }
        if (showApps) apps.forEach { (label, pkg) ->
         Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = pkg in excluded, onCheckedChange = { checked -> excluded = if (checked) excluded + pkg else excluded - pkg })
          Column { Text(label); Text(pkg, style = MaterialTheme.typography.labelSmall) }
         }
        }
       }
      }
      Text("Development build · 0.2.5")
     }
    }
   }
  }
 }
 private fun checkNetwork(): String {
  val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "No active network"
  val transport = when {
   caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
   caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
   caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
   caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
   else -> "Other"
  }
  return transport + if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) " · Internet validated" else " · Internet not validated"
 }
}
