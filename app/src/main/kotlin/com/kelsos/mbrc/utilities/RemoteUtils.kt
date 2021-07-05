package com.kelsos.mbrc.utilities

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

object RemoteUtils {

  @Throws(PackageManager.NameNotFoundException::class)
  fun Context.getVersion(): String {
    return packageManager.getPackageInfo(packageName, 0).versionName
  }

  @Throws(PackageManager.NameNotFoundException::class)
  fun Context.getVersionCode(): Long {
    return PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
  }

  fun bitmapFromFile(path: String): Bitmap? = runBlocking {
    return@runBlocking try {
      withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.RGB_565
        BitmapFactory.decodeFile(path, options)
      }
    } catch (e: Exception) {
      Timber.v(e)
      null
    }
  }

  private fun coverBitmap(coverPath: String): Bitmap? {
    val cover = File(coverPath)
    return bitmapFromFile(cover.absolutePath)
  }

  fun coverBitmapSync(coverPath: String): Bitmap? = try {
    coverBitmap(coverPath)
  } catch (e: Exception) {
    null
  }

  fun sha1(input: String) = hashString("SHA-1", input)

  private fun hashString(type: String, input: String): String {
    val HEX_CHARS = "0123456789ABCDEF"
    val bytes = MessageDigest
      .getInstance(type)
      .digest(input.toByteArray())
    val result = StringBuilder(bytes.size * 2)

    bytes.forEach {
      val i = it.toInt()
      result.append(HEX_CHARS[i shr 4 and 0x0f])
      result.append(HEX_CHARS[i and 0x0f])
    }

    return result.toString()
  }
}
