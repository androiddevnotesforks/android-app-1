package com.kelsos.mbrc.core.networking.discovery

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiscoveryMessageTest {
  @Test
  fun `a discovered host becomes a connection under the name it announced`() {
    val message = DiscoveryMessage(
      name = "TestMusicBee",
      address = "192.168.1.200",
      port = 3001,
      context = "notify"
    )

    val connection = message.toConnection()

    assertThat(connection.name).isEqualTo("TestMusicBee")
    assertThat(connection.address).isEqualTo("192.168.1.200")
    assertThat(connection.port).isEqualTo(3001)
  }

  /**
   * A discovered host is one of possibly several on the network and has no row yet, so it can
   * neither claim the default slot nor carry an id the database has not assigned.
   */
  @Test
  fun `a discovered host is neither default nor already persisted`() {
    val connection = DiscoveryMessage(name = "MusicBee", address = "10.0.0.2", port = 3000)
      .toConnection()

    assertThat(connection.isDefault).isFalse()
    assertThat(connection.id).isEqualTo(0)
  }
}
