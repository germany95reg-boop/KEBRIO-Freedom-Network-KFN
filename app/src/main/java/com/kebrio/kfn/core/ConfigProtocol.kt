package com.kebrio.kfn.core

object ConfigProtocol {
 private val obfuscationFields = setOf("jc", "jmin", "jmax", "s1", "s2", "s3", "s4", "h1", "h2", "h3", "h4", "i1", "i2", "i3", "i4", "i5")
 fun detect(raw: String): Protocol? {
  val text = raw.trim().removePrefix("\uFEFF").trim()
  if (text.isEmpty() || text.toByteArray(Charsets.UTF_8).size > 65536) return null.removePrefix("\uFEFF").trim()
  if (text.isEmpty() || text.toByteArray(Charsets.UTF_8).size > 65536) return null
  if (text.startsWith("vless://", true)) return Protocol.VLESS
  if (text.startsWith("ss://", true)) return Protocol.SHADOWSOCKS
  val lines = text.lines().map { it.substringBefore('#').trim() }
  if (lines.none { it.equals("[Interface]", true) }) return null
  if (lines.any { '=' in it && it.substringBefore('=').trim().lowercase(java.util.Locale.ROOT) in obfuscationFields }) return Protocol.AMNEZIA_WG
  return Protocol.WIREGUARD
 }
}
