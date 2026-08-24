package com.kelsos.mbrc.feature.widgets.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Rebuilds every widget instance after [WidgetTrampolineGuard] intercepted a broken widget tap.
 *
 * The tap that reached the guard came from RemoteViews whose click templates no longer carry what
 * Glance expects, so regenerating them is the only useful thing left to do with it.
 */
class WidgetHealReceiver : BroadcastReceiver() {
  @Suppress("TooGenericExceptionCaught") // Healing a widget must never crash the receiver.
  override fun onReceive(context: Context, intent: Intent) {
    val pendingResult = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
      try {
        NormalWidget.updateAll(context)
        SmallWidget.updateAll(context)
      } catch (e: Exception) {
        Timber.e(e, "Failed to heal the widgets after a broken trampoline tap")
      } finally {
        pendingResult.finish()
      }
    }
  }
}
