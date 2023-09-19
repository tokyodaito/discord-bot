package bot

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.VoiceChannel
import manager.GuildManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import service.GodmodeService
import service.MessageService

internal class Observers(private val commands: MutableMap<String, Command>) {
    private val messageService = MessageService()

    internal fun setEventObserver(client: GatewayDiscordClient) {
        observeMessageEvents(client)
        observeVoiceEvents(client)
    }

    private fun observeMessageEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(MessageCreateEvent::class.java).flatMap { event ->
            val guildId = event?.guildId?.orElse(null) ?: return@flatMap Mono.empty()
            val musicManager = GuildManager.getGuildMusicManager(guildId)

            Mono.just(event.message.content).flatMap { content ->
                if (musicManager.godMode && event.message.author.orElse(null)?.id?.asString() != GodmodeService.godModeUserId) {
                    Mono.empty()
                } else {
                    Flux.fromIterable(commands.entries)
                        .filter { entry -> content.startsWith(Bot.prefix + entry.key, ignoreCase = true) }
                        .flatMap { entry -> entry.value.execute(event) }.next()
                }
            }
        }.subscribe()
    }

    private fun observeVoiceEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(VoiceStateUpdateEvent::class.java).flatMap { event ->
            val guildId = event.current.guildId
            val selfId = client.selfId
            val oldChannelId = event.old.orElse(null)?.channelId?.orElse(null)
            val currentChannelId = event.current.channelId.orElse(null)

            if (oldChannelId == null && currentChannelId == null) {
                println("No channel state change")
                return@flatMap Mono.empty<Void>()
            }

            println("Old channel ID: $oldChannelId, Current channel ID: $currentChannelId")

            Mono.defer {
                oldChannelId?.let { oldId ->
                    println("Old channel")
                    client.getChannelById(oldId).ofType(VoiceChannel::class.java).flatMap { channel ->
                        channel.voiceStates.collectList().flatMap { voiceStates ->
                            handleVoiceState(selfId, guildId, channel, voiceStates)
                        }
                    }.onErrorResume {
                        println("Error: $it")
                        Mono.empty()
                    }
                } ?: Mono.empty<Void>()
            }.then(Mono.defer {
                currentChannelId?.let { currentId ->
                    println("Current channel")
                    client.getChannelById(currentId).ofType(VoiceChannel::class.java).flatMap { channel ->
                        channel.voiceStates.collectList().flatMap { voiceStates ->
                            handleVoiceState(selfId, guildId, channel, voiceStates)
                        }
                    }.onErrorResume { Mono.empty<Void>() }
                } ?: Mono.empty<Void>()
            })
        }.doOnError { error ->
            println("An error occurred: ${error.message}")
            error.printStackTrace()
        }.subscribe()
    }


    private fun handleVoiceState(
        selfId: Snowflake, guildId: Snowflake, channel: VoiceChannel, voiceStates: List<VoiceState>
    ): Mono<Void> {
        return if (voiceStates.size == 1 && voiceStates[0].userId == selfId) {
            println("Only bot is present in the voice channel. Disconnecting and clearing the queue.")
            channel.sendDisconnectVoiceState().then(Mono.fromRunnable<Void?> {
                GuildManager.getGuildMusicManager(guildId).scheduler.clearQueue()
            }.flatMap {
                GuildManager.getGuildMusicManager(guildId).scheduler.currentEvent?.let { it1 ->
                    messageService.sendMessage(
                        it1, "Воспроизведение остановлено"
                    )
                }
            })
        } else {
            println("More than one member is present in the voice channel.")
            Mono.empty()
        }
    }
}