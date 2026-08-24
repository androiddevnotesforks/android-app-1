package com.kelsos.mbrc.feature.widgets.glance

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

private const val TRAMPOLINE = "androidx.glance.appwidget.action.InvisibleActionTrampolineActivity"
private const val ACTION_TYPE_KEY = "ACTION_TYPE"
private const val ACTION_INTENT_KEY = "ACTION_INTENT"
private const val TEST_ACTION = "com.kelsos.mbrc.test.ACTION"

@RunWith(RobolectricTestRunner::class)
class WidgetTrampolineGuardTest {
  private lateinit var application: Application

  @Suppress("UNCHECKED_CAST")
  private val trampolineClass =
    Class.forName(TRAMPOLINE) as Class<Activity>

  @Before
  fun setUp() {
    application = ApplicationProvider.getApplicationContext()
    shadowOf(application).clearBroadcastIntents()
  }

  private fun installGuard() {
    application.registerActivityLifecycleCallbacks(WidgetTrampolineGuard())
  }

  private fun trampolineIntent(type: String?, target: Intent?): Intent =
    Intent(application, trampolineClass).apply {
      data = Uri.parse("glance-action://widget/BROADCAST")
      type?.let { putExtra(ACTION_TYPE_KEY, it) }
      target?.let { putExtra(ACTION_INTENT_KEY, it) }
    }

  private fun launch(intent: Intent) {
    Robolectric.buildActivity(trampolineClass, intent).use { it.create() }
  }

  private fun sentBroadcasts(): List<Intent> = shadowOf(application).broadcastIntents

  /**
   * The control for the test below: without the guard Glance's `requireNotNull` still throws, so
   * this pins the crash the guard is there to absorb rather than assuming it.
   */
  @Test
  fun `a trampoline intent without a target throws when the guard is not installed`() {
    val thrown = runCatching { launch(trampolineIntent(type = null, target = null)) }
      .exceptionOrNull()

    assertThat(thrown).isInstanceOf(IllegalArgumentException::class.java)
    assertThat(thrown).hasMessageThat().contains("without specifying target intent")
  }

  @Test
  fun `a healthy trampoline intent still fires its own action`() {
    installGuard()

    launch(trampolineIntent(type = "BROADCAST", target = Intent(TEST_ACTION)))

    assertThat(sentBroadcasts().map { it.action }).containsExactly(TEST_ACTION)
  }

  @Test
  fun `a trampoline intent without a target heals the widget instead of throwing`() {
    installGuard()

    launch(trampolineIntent(type = null, target = null))

    val broadcasts = sentBroadcasts()
    assertThat(broadcasts).hasSize(1)
    assertThat(broadcasts.single().component?.className)
      .isEqualTo(WidgetHealReceiver::class.java.name)
  }

  @Test
  fun `a trampoline intent with a target but no type recovers the type from its data uri`() {
    installGuard()

    launch(trampolineIntent(type = null, target = Intent(TEST_ACTION)))

    assertThat(sentBroadcasts().map { it.action }).containsExactly(TEST_ACTION)
  }

  @Test
  fun `a trampoline intent with an unusable data uri falls back to healing the widget`() {
    installGuard()
    val intent = trampolineIntent(type = null, target = Intent(TEST_ACTION)).apply {
      data = Uri.parse("https://example.com/BROADCAST")
    }

    launch(intent)

    assertThat(sentBroadcasts().single().component?.className)
      .isEqualTo(WidgetHealReceiver::class.java.name)
  }

  @Test
  fun `other activities are left alone`() {
    val guard = WidgetTrampolineGuard()
    val activity = Robolectric.buildActivity(Activity::class.java).get()
    activity.intent = Intent(TEST_ACTION)

    guard.onActivityPreCreated(activity, null)

    assertThat(activity.intent.extras).isNull()
  }
}
