import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Member
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import service.VoiceChannelService
import java.util.*
import kotlin.test.assertTrue

class VoiceChannelServiceTest {
    private val service = VoiceChannelService()

    @Test
    fun `checkUserInVoiceChannel returns true when user in channel`() {
        val event = mockk<MessageCreateEvent>()
        val member = mockk<Member>()
        val voiceState = mockk<VoiceState>()
        every { event.member } returns Optional.of(member)
        every { member.voiceState } returns Mono.just(voiceState)
        every { voiceState.channelId } returns Optional.of(Snowflake.of("1"))

        val result = service.checkUserInVoiceChannel(event).block()!!

        assertTrue(result)
    }
}

