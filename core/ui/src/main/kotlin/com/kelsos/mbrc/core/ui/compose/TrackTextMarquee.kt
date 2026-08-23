package com.kelsos.mbrc.core.ui.compose

import android.content.Context
import android.database.ContentObserver
import android.provider.Settings
import androidx.compose.foundation.basicMarquee
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/**
 * The marquee used by every track info line: the player title, artist and album, and the same two
 * lines in the mini control.
 *
 * Exists so the five call sites share one tuning point. `basicMarquee` defaults to
 * `MarqueeDefaults.Iterations = 3`, which is why a long title stopped scrolling roughly a minute
 * into a track and only restarted when the screen recomposed (see #328). Here it loops for as long
 * as the text is on screen.
 *
 * Motion is opt-out: `basicMarquee` runs its animation under a fixed motion duration scale, so it
 * ignores the system animation setting and would otherwise keep scrolling forever for someone who
 * has turned animations off. [motionEnabled] restores that setting. Callers pair this with
 * `TextOverflow.Ellipsis` so the text still degrades readably when the marquee is inert.
 */
@Composable
fun Modifier.trackTextMarquee(): Modifier = if (motionEnabled()) {
  basicMarquee(iterations = Int.MAX_VALUE)
} else {
  this
}

/**
 * Whether the system is currently animating. False when animations are off, either through
 * Developer Options or through a reduce motion accessibility setting, both of which land on an
 * animator duration scale of zero.
 *
 * Observed rather than read per recomposition: the player screen recomposes on every progress
 * tick, and [Settings.Global] reads cross a binder.
 */
@Composable
private fun motionEnabled(): Boolean {
  val context = LocalContext.current
  var enabled by remember(context) { mutableStateOf(context.animatorDurationScale() != 0f) }

  DisposableEffect(context) {
    val resolver = context.contentResolver
    val observer = object : ContentObserver(null) {
      override fun onChange(selfChange: Boolean) {
        enabled = context.animatorDurationScale() != 0f
      }
    }
    resolver.registerContentObserver(
      Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
      false,
      observer
    )
    // The setting may have changed between the initial read and the registration landing.
    enabled = context.animatorDurationScale() != 0f
    onDispose { resolver.unregisterContentObserver(observer) }
  }

  return enabled
}

// Internal rather than private: the ContentObserver below is an inner class, and reaching a
// private top-level function from it costs a synthetic accessor.
internal fun Context.animatorDurationScale(): Float = Settings.Global.getFloat(
  contentResolver,
  Settings.Global.ANIMATOR_DURATION_SCALE,
  1f
)
