package com.kebrio.kfn.core

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WireGuardController private constructor(context: Context) {
 private val backend = GoBackend(context.applicationContext)
 private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
 private val lock = Mutex()
 data class Traffic(val received: Long, val sent: Long)
 private val _traffic = MutableStateFlow<Traffic?>(null)
 val traffic = _traffic.asStateFlow()
 private val _status = MutableStateFlow("Disconnected")
 val status = _status.asStateFlow()
 private val tunnel = object : Tunnel {
  override fun getName() = "kfn"
  override fun onStateChange(newState: Tunnel.State) {
   _status.value = if (newState == Tunnel.State.UP) "Tunnel up — remote handshake not verified" else "Disconnected"
  }
 }
 init {
  scope.launch {
   while (isActive) {
    lock.withLock {
     try {
      _traffic.value = if (backend.getState(tunnel) == Tunnel.State.UP) {
       val stats = backend.getStatistics(tunnel)
       Traffic(stats.totalRx(), stats.totalTx())
      } else null
     } catch (e: Exception) {
      if (e is CancellationException) throw e
      _traffic.value = null
     }
    }
    delay(2000)
   }
  }
 }
 fun connect(raw: String, excludedPackages: Set<String> = emptySet()) {
  scope.launch { lock.withLock {
   try {
    if (backend.getState(tunnel) == Tunnel.State.UP) return@withLock
    _status.value = "Starting tunnel"
    require(raw.length in 1..65536)
    val effective = if (excludedPackages.isEmpty()) raw else {
     require(excludedPackages.all { it.matches(Regex("[A-Za-z0-9_.]+")) })
     val lines = raw.lines().toMutableList()
     require(lines.none { it.substringBefore('=').trim().equals("IncludedApplications", true) || it.substringBefore('=').trim().equals("ExcludedApplications", true) }) { "Existing application policy" }
     val index = lines.indexOfFirst { it.trim().equals("[Interface]", true) }
     require(index >= 0)
     lines.add(index + 1, "ExcludedApplications = " + excludedPackages.sorted().joinToString(", "))
     lines.joinToString("\n")
    }
    val config = Config.parse(effective.byteInputStream(Charsets.UTF_8))
    val state = backend.setState(tunnel, Tunnel.State.UP, config)
    _status.value = if (state == Tunnel.State.UP) "Tunnel up — remote handshake not verified" else "Tunnel did not start"
   } catch (e: Exception) {
    if (e is CancellationException) throw e
    _status.value = "Connection failed — check configuration and VPN permission"
   }
  } }
 }
 fun disconnect() {
  scope.launch { lock.withLock {
   try {
    backend.setState(tunnel, Tunnel.State.DOWN, null)
    _traffic.value = null
    _status.value = "Disconnected"
   } catch (e: Exception) {
    if (e is CancellationException) throw e
    _status.value = "Disconnect failed — check Android VPN settings"
   }
  } }
 }
 companion object {
  @Volatile private var instance: WireGuardController? = null
  fun get(context: Context): WireGuardController = instance ?: synchronized(this) {
   instance ?: WireGuardController(context).also { instance = it }
  }
 }
}
