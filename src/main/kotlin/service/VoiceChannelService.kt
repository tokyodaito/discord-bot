package service

import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.channel.VoiceChannel
import manager.GuildManager
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration

class VoiceChannelService {
    fun join(event: MessageCreateEvent): Mono<VoiceChannel> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.justOrEmpty(event.member)
            .flatMap { it.voiceState }
            .flatMap { it.channel }
            .flatMap { channel ->
                channel.join { spec -> spec.setProvider(musicManager.provider) }
                    .thenReturn(channel)
            }.onErrorResume {
                println("Error join: $it")
                Mono.empty()
            }.retryWhen(Retry.backoff(4, Duration.ofSeconds(5)))
            .onErrorResume {
                println("Error in disconnect: $it")
                Mono.empty()
            }
    }

    fun disconnect(event: MessageCreateEvent): Mono<Void> {
        val musicManager = GuildManager.getGuildMusicManager(event)

        return Mono.justOrEmpty(event.member.orElse(null))
            .flatMap { member ->
                member.voiceState.flatMap { voiceState ->
                    voiceState.channel.flatMap { channel ->
                        musicManager.player.removeListener(musicManager.scheduler)
                        channel.sendDisconnectVoiceState()
                    }
                }
            }.retryWhen(Retry.backoff(4, Duration.ofSeconds(5)))
            .onErrorResume {
                println("Error in disconnect: $it")
                Mono.empty()
            }
    }


    fun checkUserInVoiceChannelWithBot(event: MessageCreateEvent): Mono<Boolean> {
        return checkBotInVoiceChannel(event).flatMap { botIsInVoiceChannel ->
            if (botIsInVoiceChannel) {
                checkUserInVoiceChannel(event).flatMap { isInVoiceChannel ->
                    if (isInVoiceChannel) {
                        Mono.justOrEmpty(event.member.orElse(null)).flatMap { member ->
                            member.voiceState.flatMap { userVoiceState ->
                                event.client.self.flatMap { self ->
                                    self.asMember(event.guildId.orElse(null)).flatMap { botMember ->
                                        botMember.voiceState.map { botVoiceState ->
                                            println(userVoiceState.channelId == botVoiceState.channelId)
                                            userVoiceState.channelId == botVoiceState.channelId
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Mono.just(false)
                    }
                }
            } else {
                Mono.just(false)
            }
        }.retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .onErrorReturn(false)
    }

    fun checkUserInVoiceChannel(event: MessageCreateEvent): Mono<Boolean> {
        return Mono.justOrEmpty(event.member.orElse(null))
            .flatMap { member ->
                member.voiceState.map { voiceState ->
                    voiceState.channelId.isPresent
                }
            }
            .defaultIfEmpty(false)
            .onErrorReturn(false)
    }

    fun checkBotInVoiceChannel(event: MessageCreateEvent): Mono<Boolean> {
        return event.client.self
            .flatMap { self ->
                event.guild.flatMap { guild ->
                    guild.getMemberById(self.id)
                        .flatMap { member ->
                            member.voiceState.map { voiceState ->
                                voiceState.channelId.isPresent
                            }
                        }
                }
            }
            .defaultIfEmpty(false)
            .retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .onErrorReturn(false)
    }

}