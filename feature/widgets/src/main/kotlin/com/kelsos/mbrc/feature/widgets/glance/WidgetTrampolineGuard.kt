package com.kelsos.mbrc.feature.widgets.glance

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import timber.log.Timber

/**
 * Stops a stale widget tap from crashing the app inside Glance's trampoline activity.
 *
 * Glance wraps a widget click in an intent aimed at `InvisibleActionTrampolineActivity`, carrying
 * the real action in the `ACTION_INTENT` extra and its kind in `ACTION_TYPE`. When either extra is
 * missing the trampoline throws out of `requireNotNull` in `launchTrampolineAction`, with
 * "List adapter activity trampoline invoked without specifying target intent.".
 *
 * The trigger for the missing extras is still unknown, so this is not a root cause fix: it only
 * makes the throwing path non-fatal. `Activity.performCreate` dispatches `onActivityPreCreated`
 * before `onCreate`, which is the one chance to repair the intent before Glance reads it. A
 * repaired intent becomes a broadcast to [WidgetHealReceiver], so the tap that used to crash
 * instead regenerates the widget's RemoteViews and their click templates.
 *
 * Only the invisible trampoline is guarded. The other one, `ActionTrampolineActivity`, is used for
 * activity actions on pre-Q devices only, and `onActivityPreCreated` does not exist before Q.
 */
class WidgetTrampolineGuard internal constructor() : Application.ActivityLifecycleCallbacks {
  override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (activity.javaClass.name != GLANCE_INVISIBLE_TRAMPOLINE) return
    val intent = activity.intent ?: return
    repair(activity, intent)
  }

  private fun repair(activity: Activity, intent: Intent) {
    val hasTarget = intent.hasExtra(ACTION_INTENT_KEY)
    val type = intent.getStringExtra(ACTION_TYPE_KEY)
    if (hasTarget && type != null) return

    if (hasTarget) {
      val recovered = trampolineTypeFromData(intent)
      if (recovered != null) {
        Timber.w("Widget trampoline intent had no type, recovered %s from its data uri", recovered)
        intent.putExtra(ACTION_TYPE_KEY, recovered)
        return
      }
    }

    Timber.w("Widget trampoline intent was missing its target, healing the widget instead")
    intent.putExtra(ACTION_TYPE_KEY, BROADCAST_TYPE)
    intent.putExtra(ACTION_INTENT_KEY, Intent(activity, WidgetHealReceiver::class.java))
  }

  /**
   * Glance builds the trampoline intent's data uri as `glance-action://<host>/<TYPE>`, so the type
   * can be read back from there when only the extra went missing.
   */
  private fun trampolineTypeFromData(intent: Intent): String? {
    val data = intent.data ?: return null
    if (data.scheme != TRAMPOLINE_SCHEME) return null
    return data.path?.trimStart('/')?.takeIf { it in TRAMPOLINE_TYPES }
  }

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

  override fun onActivityStarted(activity: Activity) = Unit

  override fun onActivityResumed(activity: Activity) = Unit

  override fun onActivityPaused(activity: Activity) = Unit

  override fun onActivityStopped(activity: Activity) = Unit

  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

  override fun onActivityDestroyed(activity: Activity) = Unit

  companion object {
    private const val GLANCE_INVISIBLE_TRAMPOLINE =
      "androidx.glance.appwidget.action.InvisibleActionTrampolineActivity"
    private const val ACTION_TYPE_KEY = "ACTION_TYPE"
    private const val ACTION_INTENT_KEY = "ACTION_INTENT"
    private const val BROADCAST_TYPE = "BROADCAST"
    private const val TRAMPOLINE_SCHEME = "glance-action"
    private val TRAMPOLINE_TYPES =
      setOf("ACTIVITY", "BROADCAST", "SERVICE", "FOREGROUND_SERVICE", "CALLBACK")

    /**
     * Registers the guard. A no-op before Q, where `onActivityPreCreated` is never dispatched.
     */
    fun install(application: Application) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return
      }
      application.registerActivityLifecycleCallbacks(WidgetTrampolineGuard())
    }
  }
}
