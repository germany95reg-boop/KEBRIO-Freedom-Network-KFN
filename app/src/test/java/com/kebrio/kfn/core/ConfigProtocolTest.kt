package com.kebrio.kfn.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfigProtocolTest {
 @Test fun plainWireGuard() {
  assertEquals(Protocol.WIREGUARD, ConfigProtocol.detect("[Interface]\nAddress = 10.0.0.2/32\n[Peer]"))
 }
 @Test fun amneziaFields() {
  for (field in listOf("Jc", "Jmin", "Jmax", "S1", "H4", "I5")) {
   assertEquals(Protocol.AMNEZIA_WG, ConfigProtocol.detect("[Interface]\n$field = 1"))
  }
 }
 @Test fun commentsAreNotFields() {
  assertEquals(Protocol.WIREGUARD, ConfigProtocol.detect("[Interface]\n# Jc = 4"))
 }
 @Test fun uriSchemes() {
  assertEquals(Protocol.VLESS, ConfigProtocol.detect("vless://example"))
  assertEquals(Protocol.SHADOWSOCKS, ConfigProtocol.detect("ss://example"))
 }
 @Test fun importedBom() {
  assertEquals(Protocol.WIREGUARD, ConfigProtocol.detect("\uFEFF[Interface]"))
 }
 @Test fun oversizedInput() {
  assertNull(ConfigProtocol.detect("vless://" + "a".repeat(65536)))
  assertNull(ConfigProtocol.detect("[Interface]\n#" + "я".repeat(32768)))
 }
 @Test fun utf8Bom() {
  assertEquals(Protocol.WIREGUARD, ConfigProtocol.detect("\uFEFF[Interface]\n[Peer]"))
 }
 @Test fun oversizedAsciiConfig() {
  assertNull(ConfigProtocol.detect("[Interface]\n" + "x".repeat(65536)))
 }
 @Test fun unknown() {
  assertNull(ConfigProtocol.detect(""))
  assertNull(ConfigProtocol.detect("https://example.invalid"))
 }
}
