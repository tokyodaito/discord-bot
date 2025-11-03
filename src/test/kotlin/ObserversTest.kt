import bot.Observers
import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.`object`.VoiceState
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import manager.GuildManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import reactor.core.publisher.Mono

class ObserversTest {

    @AfterEach
    fun tearDown() {
        unmockkObject(GuildManager)
    }

    @Test
    fun `handleVoiceLeaving ignores non bot users`() {
        val observers = Observers(mutableMapOf())
        val method = Observers::class.java.getDeclaredMethod(
            "handleVoiceLeaving",
            VoiceStateUpdateEvent::class.java,
            GatewayDiscordClient::class.java,
            Snowflake::class.java,
            Snowflake::class.java
        ).apply { isAccessible = true }

        val event = mockk<VoiceStateUpdateEvent>()
        val client = mockk<GatewayDiscordClient>()
        val voiceState = mockk<VoiceState>()
        val guildId = Snowflake.of(1L)
        val userId = Snowflake.of(2L)
        val botId = Snowflake.of(3L)

        every { event.current } returns voiceState
        every { event.client } returns client
        every { voiceState.guildId } returns guildId
        every { voiceState.userId } returns userId
        every { client.selfId } returns botId
        every { client.self } returns Mono.error(RuntimeException("should not be called"))

        mockkObject(GuildManager)
        every { GuildManager.getGuildMusicManager(any<Snowflake>()) } throws AssertionError("should not be called")

        val result = method.invoke(
            observers,
            event,
            client,
            Snowflake.of(4L),
            null
        ) as Mono<*>

        assertDoesNotThrow { result.blockOptional() }
    }
}

