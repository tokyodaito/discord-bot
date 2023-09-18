package service

import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.channel.VoiceChannel
import manager.GuildManager
import reactor.core.publisher.Mono

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
    }

    fun disconnect(event: MessageCreateEvent): Mono<Void?> {
        return Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
            Mono.justOrEmpty(member?.voiceState?.block()).flatMap { voiceState ->
                Mono.justOrEmpty(voiceState?.channel?.block()).flatMap { channel ->
                    channel?.sendDisconnectVoiceState()
                }
            }
        }
    }

    fun checkUser(event: MessageCreateEvent): Mono<Boolean> {
        return Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
            member.voiceState.flatMap { userVoiceState ->
                event.client.self.flatMap { self ->
                    self.asMember(event.guildId.orElse(null)).flatMap { botMember ->
                        botMember.voiceState.map { botVoiceState ->
                            userVoiceState.channelId == botVoiceState.channelId
                        }
                    }
                }
            }
        }.defaultIfEmpty(false)
    }


}