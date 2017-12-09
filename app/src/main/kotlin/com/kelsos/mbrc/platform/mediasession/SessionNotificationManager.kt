package com.kelsos.mbrc.platform.mediasession

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import com.kelsos.mbrc.R
import com.kelsos.mbrc.content.activestatus.PlayerState
import com.kelsos.mbrc.events.ConnectionStatusChangeEvent
import com.kelsos.mbrc.events.CoverChangedEvent
import com.kelsos.mbrc.events.PlayStateChange
import com.kelsos.mbrc.events.TrackInfoChangeEvent
import com.kelsos.mbrc.events.bus.RxBus
import com.kelsos.mbrc.networking.connections.Connection
import com.kelsos.mbrc.platform.mediasession.RemoteViewIntentBuilder.NEXT
import com.kelsos.mbrc.platform.mediasession.RemoteViewIntentBuilder.OPEN
import com.kelsos.mbrc.platform.mediasession.RemoteViewIntentBuilder.PLAY
import com.kelsos.mbrc.platform.mediasession.RemoteViewIntentBuilder.PREVIOUS
import com.kelsos.mbrc.platform.mediasession.RemoteViewIntentBuilder.getPendingIntent
import com.kelsos.mbrc.utilities.RemoteUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionNotificationManager
@Inject
constructor(
  bus: RxBus,
  private val context: Application,
  private val sessionManager: RemoteSessionManager,
  private val model: SessionStatusModel,
  private val notificationManager: NotificationManager
) {
  private var notification: Notification? = null
  private val previous: String
  private val play: String
  private val next: String

  init {
    bus.register(this, TrackInfoChangeEvent::class.java) { this.handleTrackInfo(it) }
    bus.register(this, CoverChangedEvent::class.java) { this.coverChanged(it.path) }
    bus.register(this, PlayStateChange::class.java) { this.playStateChanged(it) }
    bus.register(this, ConnectionStatusChangeEvent::class.java) { this.connectionChanged(it) }
    previous = context.getString(R.string.notification_action_previous)
    play = context.getString(R.string.notification_action_play)
    next = context.getString(R.string.notification_action_next)
    createNotificationChannels()
  }

  private fun handleTrackInfo(event: TrackInfoChangeEvent) {
    model.trackInfo = event.trackInfo
    update()
  }

  private fun coverChanged(path: String) {
    val coverFile = File(path)
    model.cover = if (coverFile.exists()) {
      RemoteUtils.bitmapFromFile(coverFile.absolutePath)
    } else {
      null
    }
    update()
  }

  private fun playStateChanged(event: PlayStateChange) {
    model.playState = event.state
    update()
  }

  fun update() {
    notification = createBuilder().build().also { notification ->
      notificationManager.notify(NOW_PLAYING_PLACEHOLDER, notification)
    }
  }

  private fun connectionChanged(event: ConnectionStatusChangeEvent) {

    if (event.status == Connection.OFF) {
      cancelNotification(NOW_PLAYING_PLACEHOLDER)
    } else {
      update()
    }
  }

  private fun createNotificationChannels() {
    val channel = channel()
    if (channel === null) {
      return
    }
    notificationManager.createNotificationChannel(channel)
  }

  private fun createBuilder(): NotificationCompat.Builder {
    val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
    mediaStyle.setMediaSession(sessionManager.mediaSessionToken)

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
    val resId = if (model.playState == PlayerState.PLAYING) {
      R.drawable.ic_action_pause
    } else {
      R.drawable.ic_action_play
    }

    builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setSmallIcon(R.drawable.ic_mbrc_status)
      .setStyle(mediaStyle.setShowActionsInCompactView(1, 2))
      .addAction(previousAction)
      .addAction(getPlayAction(resId))
      .addAction(nextAction)

    builder.priority = NotificationCompat.PRIORITY_LOW
    builder.setOnlyAlertOnce(true)

    if (model.cover != null) {
      builder.setLargeIcon(model.cover)
    } else {
      val icon = BitmapFactory.decodeResource(context.resources, R.drawable.ic_image_no_cover)
      builder.setLargeIcon(icon)
    }

    val info = model.trackInfo

    info?.let {
      builder.setContentTitle(it.title).setContentText(it.artist).setSubText(it.album)
    }

    builder.setContentIntent(getPendingIntent(OPEN, context))

    return builder
  }

  private val previousAction: Action
    get() {
      val previousIntent = getPendingIntent(PREVIOUS, context)
      return Action.Builder(R.drawable.ic_action_previous, previous, previousIntent).build()
    }

  private fun getPlayAction(playStateIcon: Int): Action {
    val playIntent = getPendingIntent(PLAY, context)

    return Action.Builder(playStateIcon, play, playIntent).build()
  }

  private val nextAction: Action
    get() {
      val nextIntent = getPendingIntent(NEXT, context)
      return Action.Builder(R.drawable.ic_action_next, next, nextIntent).build()
    }

  fun cancelNotification(notificationId: Int = NOW_PLAYING_PLACEHOLDER) {
    notificationManager.cancel(notificationId)
  }

  companion object {
    const val NOW_PLAYING_PLACEHOLDER = 15613
    const val CHANNEL_ID = "mbrc_session"

    fun channel(): NotificationChannel? {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return null
      }

      val channel = NotificationChannel(
        CHANNEL_ID,
        "MusicBee Remote",
        NotificationManager.IMPORTANCE_DEFAULT
      )

      return channel.apply {
        this.description = "MusicBee Remote: Service"
        enableLights(false)
        enableVibration(false)
        setSound(null, null)
      }
    }
  }
}