package bot

import discord4j.common.util.Snowflake
import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.VoiceStateUpdateEvent
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.channel.VoiceChannel
import manager.GuildManager.getGuildMusicManager
import manager.GuildMusicManager
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.util.retry.Retry
import service.GodmodeService
import java.time.Duration
import kotlin.random.Random

internal class Observers(private val commands: MutableMap<String, Command>) {
    internal fun setEventObserver(client: GatewayDiscordClient) {
        observeMessageEvents(client)
        observeVoiceEvents(client)
    }

    private fun observeMessageEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(MessageCreateEvent::class.java)
            .flatMap { event ->
                val guildId = event.guildId.orElse(null)
                val musicManager = getGuildMusicManager(guildId)
                if (!musicManager.checkExistsGuild()) {
                    musicManager.addGuild()
                    sendFirstMessage(event).subscribe()
                }
                processEvent(event)
            }
            .subscribeOn(Schedulers.parallel())
            .onErrorResume {
                println("Error observeMessageEvents: $it")
                Mono.empty()
            }.subscribe()
    }

    private fun observeVoiceEvents(client: GatewayDiscordClient) {
        client.eventDispatcher.on(VoiceStateUpdateEvent::class.java)
            .flatMap { event -> handleVoiceStateUpdateEvent(event, client) }
            .subscribeOn(Schedulers.parallel())
            .doOnError { error ->
                println("An error occurred: ${error.message}")
                error.printStackTrace()
            }
            .onErrorResume {
                println("Error observeVoiceEvents: $it")
                Mono.empty()
            }
            .subscribe()
    }

    private fun handleVoiceStateUpdateEvent(event: VoiceStateUpdateEvent, client: GatewayDiscordClient): Mono<Void> {
        val guildId = event.current.guildId
        val selfId = client.selfId
        val oldChannelId = event.old.orElse(null)?.channelId?.orElse(null)
        val currentChannelId = event.current.channelId.orElse(null)

        if (event.current.userId == selfId && oldChannelId == null) {
            return Mono.empty()
        }

        if (oldChannelId == null && currentChannelId == null) {
            println("No channel state change")
            return Mono.empty<Void>()
        }

        println("Old channel ID: $oldChannelId, Current channel ID: $currentChannelId")

        return handleVoiceLeaving(event, client, oldChannelId, currentChannelId)
            .then(handleVoiceJoining(client, selfId, guildId, oldChannelId))
            .then(handleVoiceJoining(client, selfId, guildId, currentChannelId))
    }

    private fun handleVoiceLeaving(
        event: VoiceStateUpdateEvent,
        client: GatewayDiscordClient,
        oldChannelId: Snowflake?,
        currentChannelId: Snowflake?
    ): Mono<Void> {
        if (oldChannelId != null && currentChannelId == null) {
            return if (event.current.userId == client.selfId) {
                stopMusic(event.current.guildId)
            } else {
                Mono.empty()
            }
        }
        return Mono.empty()
    }

    private fun handleVoiceJoining(
        client: GatewayDiscordClient,
        selfId: Snowflake,
        guildId: Snowflake,
        channelId: Snowflake?
    ): Mono<Void> {
        return channelId?.let { id ->
            client.getChannelById(id).ofType(VoiceChannel::class.java)
                .flatMap { channel ->
                    channel.voiceStates.collectList()
                        .flatMap { voiceStates -> handleVoiceState(selfId, guildId, channel, voiceStates) }
                }
                .onErrorResume {
                    println("Error: $it")
                    Mono.empty<Void>()
                }
        } ?: Mono.empty<Void>()
    }

    private fun processEvent(event: MessageCreateEvent): Mono<Void?>? {
        val guildId = event.guildId.orElse(null) ?: return Mono.empty()
        val musicManager = getGuildMusicManager(guildId)

        return Mono.just(event.message.content)
            .flatMap { content ->
                if (isGodModeEnabled(musicManager, event)) {
                    Mono.empty()
                } else {
                    executeMatchingCommand(content, event)
                }
            }
            .onErrorResume {
                println("Error processEvent: $it")
                Mono.empty()
            }
    }

    private fun isGodModeEnabled(musicManager: GuildMusicManager, event: MessageCreateEvent): Boolean {
        val authorId = event.message.author.orElse(null)?.id?.asString()
        return musicManager.godMode && authorId != GodmodeService.godModeUserId
    }

    private fun executeMatchingCommand(content: String, event: MessageCreateEvent): Mono<Void?> {
        return Flux.fromIterable(commands.entries)
            .filter { entry -> content.startsWith(Bot.prefix + entry.key, ignoreCase = true) }
            .flatMap { entry ->
                entry.value.execute(event)?.then(
                    Mono.defer {
                        if (Random.nextInt(100) < -1) {
                            sendBoostyMessage(event)
                        } else {
                            Mono.empty<Void>()
                        }
                    }
                )
            }
            .next()
    }


    private fun handleVoiceState(
        selfId: Snowflake, guildId: Snowflake, channel: VoiceChannel, voiceStates: List<VoiceState>
    ): Mono<Void> {
        return if (voiceStates.size == 1 && voiceStates[0].userId == selfId) {
            println("Only bot is present in the voice channel. Disconnecting and clearing the queue.")
            channel.sendDisconnectVoiceState().then(Mono.fromRunnable<Void?> {
                getGuildMusicManager(guildId).player.removeListener(
                    getGuildMusicManager(
                        guildId
                    ).scheduler
                )
                getGuildMusicManager(guildId).scheduler.clearQueue()
            })
        } else {
            println("More than one member is present in the voice channel.")
            Mono.empty()
        }.retryWhen(Retry.backoff(3, Duration.ofSeconds(5)))
            .onErrorResume {
                println("Error handleVoiceState: $it")
                Mono.empty()
            }
    }

    private fun isBotInVoiceChannel(
        event: VoiceStateUpdateEvent,
        client: GatewayDiscordClient
    ): Mono<Boolean> {
        return event.client.self
            .flatMap { self ->
                client.getGuildById(event.current.guildId)
                    .flatMap { guild ->
                        guild.getMemberById(self.id)
                    }
            }
            .flatMap { member ->
                member.voiceState.map { voiceState -> voiceState.channelId.isPresent }
            }
            .defaultIfEmpty(false)
    }

    private fun stopMusic(guildId: Snowflake): Mono<Void> {
        return Mono.fromCallable {
            val musicManager = getGuildMusicManager(guildId)
            musicManager.player.removeListener(musicManager.scheduler)
            musicManager.scheduler.clearQueue()
        }.then()
    }

    private fun sendBoostyMessage(event: MessageCreateEvent): Mono<Void?> {
        val reasons = listOf(
            "Потому что здесь ты найдешь уникальный контент, который я создаю исключительно для своих подписчиков.",
            "Это способ поддержать мой творческий путь и позволить мне продолжать делиться ценностями и знаниями.",
            "Подписавшись, ты первым узнаешь о новых функциях и обновлениях, которые я внедряю.",
            "Это отличная возможность для тебя предложить свои идеи и увидеть их реализацию.",
            "Подписка дает тебе доступ к эксклюзивному сообществу единомышленников, где мы можем делиться опытом и мотивацией.",
            "Ты будешь иметь возможность получать персональные советы и рекомендации по самообучению и продуктивности.",
            "Твоя подписка поможет мне освободить больше времени для исследований и разработок, что в итоге приносит пользу всем пользователям.",
            "Взаимодействие на платформе Boosty позволит тебе оставаться на связи со мной напрямую и влиять на контент.",
            "Поддержка через Boosty — это простой способ сказать \"спасибо\" за работу, которую я вкладываю в создание бота и контента.",
            "Это инвестиция в твое собственное развитие, поскольку контент, которым я делюсь, нацелен на обучение и вдохновение."
        )

        val randomReason = reasons[Random.nextInt(reasons.size)]

        val messageService = Bot.serviceComponent.getMessageService()
        return messageService.createEmbedMessage(
            event,
            "Подпишись на Boosty: https://www.youtube.com/watch?v=dQw4w9WgXcQ&pp=ygUXbmV2ZXIgZ29ubmEgZ2l2ZSB5b3UgdXA%3D",
            "Почему? $randomReason"
        ).then()
    }

    private fun sendFirstMessage(event: MessageCreateEvent): Mono<Void?> {
        val messageService = Bot.serviceComponent.getMessageService()
        return messageService.createEmbedMessage(
            event,
            "Приветствую!",
            "Я — \"Большой брат\", музыкальный бот, который призван сделать пребывание здесь ещё более приятным. Знаешь что самое крутое? Я полностью бесплатный! \uD83C\uDF89\nМой создатель верит в свободу музыки для всех, и поэтому никаких подписок или скрытых платежей — просто настройся на правильный вайб и наслаждайся. Если тебе нравится, что я делаю, и ты хочешь узнать больше о моих функциях или о самом создателе, загляни на его страницу Boosty. Там ты найдешь массу интересного контента, советы по саморазвитию и многое другое. Поддержка через Boosty — отличный способ сказать \"спасибо\" и помочь проекту стать ещё лучше.\n" +
                    "\n" +
                    "Так что давай вместе сделаем этот сервер местом, где всегда звучит музыка и царит радость! \uD83C\uDFB6\n" +
                    "\n" +
                    "Если возникнут вопросы по моему функционалу или ты захочешь предложить новые идеи, просто напиши, и я буду рад помочь.\n" +
                    "\n" +
                    "Давай начнем нашу музыкальную авантюру! \uD83D\uDE80"
        ).then()
    }
}