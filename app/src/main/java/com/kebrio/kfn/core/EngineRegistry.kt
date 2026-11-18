package com.kebrio.kfn.core

class EngineRegistry(engines: List<TunnelEngine> = emptyList()) {
 private val byProtocol = engines.associateBy { it.protocol }
 init { require(byProtocol.size == engines.size) }
 fun availableProtocols(): Set<Protocol> = byProtocol.keys.toSet()
 fun resolve(protocol: Protocol): TunnelEngine =
  byProtocol[protocol] ?: throw IllegalStateException("Protocol engine not installed: $protocol")
}
