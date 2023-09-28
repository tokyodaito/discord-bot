package service

import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.VoiceChannel
import manager.GuildManager
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.util.*

class VoiceChannelService {
    fun join(event: MessageCreateEvent): Mono<VoiceChannel> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.justOrEmpty(event.member)
            .flatMap { it.voiceState }
            .flatMap { it.channel }
            .flatMap { channel ->
                channel.join { spec -> spec.setProvider(musicManager.provider) }
                    .thenReturn(channel)
            }
            .retryWhen(Retry.backoff(4, Duration.ofSeconds(5)))
            .onErrorResume {
                println("Error: $it")
                Mono.empty()
            }
    }

    fun disconnect(event: MessageCreateEvent): Mono<Void> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return getMemberVoiceState(event)
            .flatMap { voiceState ->
                musicManager.player.removeListener(musicManager.scheduler)
                voiceState.channel.flatMap { channel ->
                    channel.sendDisconnectVoiceState()
                }
            }
            .retryWhen(Retry.backoff(4, Duration.ofSeconds(5)))
            .onErrorResume(logError("Error in disconnect"))
    }

    fun checkUserInVoiceChannelWithBot(event: MessageCreateEvent): Mono<Boolean> {
        return checkBotInVoiceChannel(event)
            .filter { it } // Only proceed if bot is in the voice channel
            .flatMap { checkUserInVoiceChannel(event) }
            .filter { it } // Only proceed if user is in the voice channel
            .flatMap { toMono(event.member) } // Convert Optional to Mono
            .flatMap { member -> areUserAndBotInSameChannel(member, event) }
            .defaultIfEmpty(false)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .onErrorReturn(false)
    }

    fun checkUserInVoiceChannel(event: MessageCreateEvent): Mono<Boolean> {
        return getMemberVoiceState(event)
            .map { voiceState -> voiceState.channelId.isPresent }
            .defaultIfEmpty(false)
            .onErrorReturn(false)
    }

    fun checkBotInVoiceChannel(event: MessageCreateEvent): Mono<Boolean> {
        return event.client.self
            .flatMap { self -> getGuildMemberVoiceState(event.guild, self.id) }
            .map { voiceState -> voiceState.channelId.isPresent }
            .defaultIfEmpty(false)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .onErrorReturn(false)
    }

    private fun getMemberVoiceState(event: MessageCreateEvent): Mono<VoiceState> {
        return Mono.justOrEmpty(event.member.orElse(null))
            .flatMap { member -> member.voiceState }
    }

    private fun getGuildMemberVoiceState(guildMono: Mono<Guild>, memberId: Snowflake): Mono<VoiceState> {
        return guildMono
            .flatMap { guild -> guild.getMemberById(memberId) }
            .flatMap { member -> member.voiceState }
    }

    private fun logError(message: String): (Throwable) -> Mono<Void> {
        return { error ->
            println("$message: $error")
            Mono.empty()
        }
    }

    private fun areUserAndBotInSameChannel(member: Member, event: MessageCreateEvent): Mono<Boolean> {
        return member.voiceState
            .flatMap { userVoiceState ->
                event.client.self
                    .flatMap { self -> toMono(event.guildId).flatMap { guildId -> self.asMember(guildId) } }
                    .flatMap { botMember -> botMember.voiceState }
                    .map { botVoiceState ->
                        userVoiceState.channelId == botVoiceState.channelId
                    }
            }
    }

    private fun <T> toMono(optional: Optional<T>): Mono<T> {
        return Mono.justOrEmpty(optional)
    }
}