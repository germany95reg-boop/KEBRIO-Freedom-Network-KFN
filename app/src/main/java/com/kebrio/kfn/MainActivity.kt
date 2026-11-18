package com.kebrio.kfn
import android.os.Bundle
import android.net.VpnService
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = darkColorScheme()) {
    var network by remember { mutableStateOf(checkNetwork()) }
    var vpnPermission by remember { mutableStateOf(VpnService.prepare(this@MainActivity) == null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vpnPermission = VpnService.prepare(this@MainActivity) == null }
    Surface(modifier = Modifier.fillMaxSize()) {
     Column(Modifier.safeDrawingPadding().fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
      Text("KEBRIO", style = MaterialTheme.typography.headlineLarge)
      Text("FREEDOM NETWORK", style = MaterialTheme.typography.labelLarge)
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("VPN disconnected", style = MaterialTheme.typography.headlineSmall)
        Text("No tunnel engine configured yet.")
        Text(if (vpnPermission) "VPN permission granted" else "VPN permission not granted")
        OutlinedButton(onClick = { val intent = VpnService.prepare(this@MainActivity); if (intent == null) vpnPermission = true else permissionLauncher.launch(intent) }) { Text("Check VPN permission") }
        Button(onClick = {}, enabled = false) { Text("Connect") }
       }
      }
      ElevatedCard(Modifier.fillMaxWidth()) {
       Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Network diagnostics", style = MaterialTheme.typography.titleLarge)
        Text(network)
        OutlinedButton(onClick = { network = checkNetwork() }) { Text("Refresh") }
       }
      }
      Text("Development build · 0.1.1")
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
