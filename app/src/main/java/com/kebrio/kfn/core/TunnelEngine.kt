package com.kebrio.kfn.core

enum class Protocol { WIREGUARD, AMNEZIA_WG, VLESS, SHADOWSOCKS }
enum class TunnelState { DISCONNECTED, CONNECTING, CONNECTED, DISCONNECTING, FAILED }

data class TunnelProfile(val id: String, val protocol: Protocol, val host: String, val port: Int) {
 init {
  require(id.isNotBlank())
  require(host.isNotBlank() && host.none { it.isWhitespace() || it.isISOControl() })
  require(port in 1..65535)
 }
}

interface TunnelEngine {
 val protocol: Protocol
 suspend fun connect(profile: TunnelProfile)
 suspend fun disconnect()
}
