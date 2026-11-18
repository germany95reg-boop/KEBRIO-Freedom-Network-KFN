package com.kebrio.kfn

import android.os.Bundle
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

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
  val controller = WireGuardController.get(this)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme()) {
    var network by remember { mutableStateOf(checkNetwork()) }
    var config by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
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
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
     if (VpnService.prepare(this@MainActivity) == null && config.isNotBlank()) {
      controller.connect(config); config = ""
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
        Text("Import or paste a configuration from your trusted VPN server. Keys are not saved by KFN.")
        OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) { Text("Import .conf") }
        if (importError.isNotEmpty()) Text(importError, color = MaterialTheme.colorScheme.error)
        OutlinedTextField(value = config, onValueChange = { if (it.length <= 65536) config = it }, label = { Text("WireGuard configuration") }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6, visualTransformation = PasswordVisualTransformation())
        Button(onClick = {
         val intent = VpnService.prepare(this@MainActivity)
         if (intent == null) { controller.connect(config); config = "" } else launcher.launch(intent)
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
      Text("Development build · 0.2.1")
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
