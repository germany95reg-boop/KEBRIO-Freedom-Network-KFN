package com.kebrio.kfn.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Single opt-in local profile. No plaintext files or cloud backup.
class ProfileVault(context: Context) {
 private val file = AtomicFile(File(context.noBackupFilesDir, "wireguard-profile.enc"))
 private val alias = "kfn.profile.v1"
 private fun key(create: Boolean): SecretKey {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias, null) as? SecretKey)?.let { return it }
  check(create) { "Profile key missing" }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256).build())
  }.generateKey()
 }
 @Synchronized fun save(raw: String) {
  val plain = raw.toByteArray(Charsets.UTF_8)
  try {
   require(plain.size in 1..65536)
   val cipher = Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.ENCRYPT_MODE, key(true))
   val iv = cipher.iv
   require(iv.size == 12)
   val encrypted = cipher.doFinal(plain)
   val stream = file.startWrite()
   try {
    stream.write(byteArrayOf(1)); stream.write(iv); stream.write(encrypted)
    file.finishWrite(stream)
   } catch (e: Exception) { file.failWrite(stream); throw e }
  } finally { plain.fill(0) }
 }
 @Synchronized fun load(): String? {
  if (!file.baseFile.exists()) return null
  val bytes = file.readFully()
  require(bytes.size in 30..65565 && bytes[0] == 1.toByte())
  val cipher = Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1,13)))
  val plain = cipher.doFinal(bytes,13,bytes.size-13)
  return try { plain.toString(Charsets.UTF_8) } finally { plain.fill(0) }
 }
 @Synchronized fun delete() {
  val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  store.deleteEntry(alias)
  file.delete()
 }
}
