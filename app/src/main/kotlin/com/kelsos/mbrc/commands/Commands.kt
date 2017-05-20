package com.kelsos.mbrc.commands

import android.app.Application
import android.content.Intent
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.kelsos.mbrc.annotations.SocketAction
import com.kelsos.mbrc.constants.Protocol
import com.kelsos.mbrc.constants.ProtocolEventType
import com.kelsos.mbrc.controller.RemoteService
import com.kelsos.mbrc.data.ProtocolPayload
import com.kelsos.mbrc.data.SocketMessage
import com.kelsos.mbrc.data.UserAction
import com.kelsos.mbrc.events.MessageEvent
import com.kelsos.mbrc.events.bus.RxBus
import com.kelsos.mbrc.interfaces.ICommand
import com.kelsos.mbrc.interfaces.IEvent
import com.kelsos.mbrc.messaging.NotificationService
import com.kelsos.mbrc.model.ConnectionModel
import com.kelsos.mbrc.model.MainDataModel
import com.kelsos.mbrc.services.ProtocolHandler
import com.kelsos.mbrc.services.ServiceDiscovery
import com.kelsos.mbrc.services.SocketService
import com.kelsos.mbrc.utilities.SettingsManager
import com.kelsos.mbrc.utilities.SocketActivityChecker
import org.threeten.bp.Instant
import org.threeten.bp.temporal.ChronoUnit
import timber.log.Timber
import java.io.IOException
import java.net.URL
import javax.inject.Inject

class CancelNotificationCommand
@Inject constructor(private val notificationService: NotificationService) : ICommand {

  override fun execute(e: IEvent) {
    notificationService.cancelNotification(NotificationService.NOW_PLAYING_PLACEHOLDER)
  }
}

class ConnectionStatusChangedCommand
@Inject constructor(
  private val model: ConnectionModel,
  private val service: SocketService,
  private val notificationService: NotificationService
) : ICommand {

  override fun execute(e: IEvent) {
    model.setConnectionState(e.dataString)

    if (model.isConnectionActive) {
      service.sendData(SocketMessage.create(Protocol.Player, "Android"))
    } else {
      notificationService.cancelNotification(NotificationService.NOW_PLAYING_PLACEHOLDER)
    }
  }
}

class HandleHandshake
@Inject constructor(
  private val handler: ProtocolHandler,
  private val model: ConnectionModel
) : ICommand {

  override fun execute(e: IEvent) {
    if (!(e.data as Boolean)) {
      handler.resetHandshake()
      model.setHandShakeDone(false)
    }
  }
}

class InitiateConnectionCommand
@Inject constructor(private val socketService: SocketService) : ICommand {

  override fun execute(e: IEvent) {
    socketService.socketManager(SocketAction.START)
  }
}

class KeyVolumeDownCommand
@Inject constructor(
  private val model: MainDataModel,
  private val bus: RxBus
) : ICommand {

  override fun execute(e: IEvent) {
    if (model.volume >= 10) {
      val mod = model.volume % 10
      val volume: Int

      if (mod == 0) {
        volume = model.volume - 10
      } else if (mod < 5) {
        volume = model.volume - (10 + mod)
      } else {
        volume = model.volume - mod
      }

      bus.post(MessageEvent.action(UserAction(Protocol.PlayerVolume, volume)))
    }
  }
}

class KeyVolumeUpCommand
@Inject
constructor(
  private val model: MainDataModel,
  private val bus: RxBus
) : ICommand {

  override fun execute(e: IEvent) {
    val volume: Int = if (model.volume <= 90) {
      val mod = model.volume % 10

      when {
        mod == 0 -> model.volume + 10
        mod < 5 -> model.volume + (10 - mod)
        else -> model.volume + (20 - mod)
      }
    } else {
      100
    }

    bus.post(MessageEvent.action(UserAction(Protocol.PlayerVolume, volume)))
  }
}

class ProcessUserAction
@Inject constructor(private val socket: SocketService) : ICommand {

  override fun execute(e: IEvent) {
    val action = e.data as UserAction
    socket.sendData(SocketMessage.create(action.context, action.data))
  }
}

class ProtocolPingHandle
@Inject constructor(
  private val service: SocketService,
  private var activityChecker: SocketActivityChecker
) : ICommand {

  override fun execute(e: IEvent) {
    activityChecker.ping()
    service.sendData(SocketMessage.create(Protocol.PONG))
  }
}

class ProtocolPongHandle
@Inject constructor() : ICommand {
  override fun execute(e: IEvent) {
    Timber.d(e.data.toString())
  }
}

class ProtocolRequest
@Inject constructor(
  private val socket: SocketService,
  private val settingsManager: SettingsManager
) : ICommand {

  override fun execute(e: IEvent) {
    val payload = ProtocolPayload(settingsManager.getClientId())
    payload.noBroadcast = false
    payload.protocolVersion = Protocol.ProtocolVersionNumber
    socket.sendData(SocketMessage.create(Protocol.ProtocolTag, payload))
  }
}

class ReduceVolumeOnRingCommand
@Inject constructor(
  private val model: MainDataModel,
  private val service: SocketService
) : ICommand {

  override fun execute(e: IEvent) {
    if (model.isMute || model.volume == 0) {
      return
    }
    service.sendData(SocketMessage.create(Protocol.PlayerVolume, (model.volume * 0.2).toInt()))
  }
}

class RestartConnectionCommand
@Inject constructor(private val socket: SocketService) : ICommand {

  override fun execute(e: IEvent) {
    socket.socketManager(SocketAction.RESET)
  }
}

class SocketDataAvailableCommand
@Inject
constructor(
  private val handler: ProtocolHandler
) : ICommand {

  override fun execute(e: IEvent) {
    handler.preProcessIncoming(e.dataString)
  }
}

class StartDiscoveryCommand
@Inject constructor(private val mDiscovery: ServiceDiscovery) : ICommand {

  override fun execute(e: IEvent) {
    mDiscovery.startDiscovery()
  }
}

class TerminateConnectionCommand
@Inject constructor(
  private val service: SocketService,
  private val model: ConnectionModel
) : ICommand {

  override fun execute(e: IEvent) {
    model.setHandShakeDone(false)
    model.setConnectionState("false")
    service.socketManager(SocketAction.TERMINATE)
  }
}

class VersionCheckCommand
@Inject
constructor(
  private val model: MainDataModel,
  private val mapper: ObjectMapper,
  private val manager: SettingsManager,
  private val bus: RxBus
) : ICommand {

  override fun execute(e: IEvent) {
    val now = Instant.now()

    if (check(MINIMUM_REQUIRED)) {
      val next = getNextCheck(true)
      if (next.isAfter(now)) {
        Timber.d("Next update required check is @ $next")
        return
      }
      bus.post(MessageEvent(ProtocolEventType.PluginUpdateRequired))
      model.minimumRequired = MINIMUM_REQUIRED
      model.pluginUpdateRequired = true
      manager.setLastUpdated(now, true)
      return
    }

    if (!manager.isPluginUpdateCheckEnabled()) {
      return
    }

    val nextCheck = getNextCheck()

    if (nextCheck.isAfter(now)) {
      Timber.d("Next update check after @ $nextCheck")
      return
    }

    val jsonNode: JsonNode
    try {
      jsonNode = mapper.readValue(URL(CHECK_URL), JsonNode::class.java)
    } catch (e1: IOException) {
      Timber.d(e1, "While reading json node")
      return
    }

    val expected = jsonNode.path("tag_name").asText().replace("v", "")

    val found = model.pluginVersion
    if (expected != found && check(expected)) {
      model.pluginUpdateAvailable = true
      bus.post(MessageEvent(ProtocolEventType.PluginUpdateAvailable))
    }

    manager.setLastUpdated(now, false)
    Timber.d("Checked for plugin update @ $now. Found: $found expected: $expected")
  }

  private fun getNextCheck(required: Boolean = false): Instant {
    val lastUpdated = manager.getLastUpdated(required)
    val days = if (required) 1L else 2L
    return lastUpdated.plus(days, ChronoUnit.DAYS)
  }

  private fun check(suggestedVersion: String): Boolean {
    val currentVersion = model.pluginVersion.toVersionArray()
    val latestVersion = suggestedVersion.toVersionArray()

    var i = 0
    val currentSize = currentVersion.size
    val latestSize = latestVersion.size
    while (i < currentSize && i < latestSize && currentVersion[i] == latestVersion[i]) {
      i++
    }

    if (i < currentSize && i < latestSize) {
      val diff = currentVersion[i].compareTo(latestVersion[i])
      return diff < 0
    }

    return false
  }

  companion object {
    private const val CHECK_URL =
      "https://api.github.com/repos/musicbeeremote/plugin/releases/latest"
    private const val MINIMUM_REQUIRED = "1.4.0"
  }
}

fun String.toVersionArray(): Array<Int> = split("\\.".toRegex())
  .dropLastWhile(String::isEmpty)
  .take(3)
  .map { it.toInt() }
  .toTypedArray()

class TerminateServiceCommand
@Inject constructor(
  private val application: Application
) : ICommand {

  override fun execute(e: IEvent) {
    if (RemoteService.SERVICE_STOPPING) {
      return
    }
    application.run {
      stopService(Intent(this, RemoteService::class.java))
    }
  }
}
