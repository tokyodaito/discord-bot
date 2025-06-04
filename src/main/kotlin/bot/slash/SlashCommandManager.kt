package bot.slash

import discord4j.core.GatewayDiscordClient
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent
import discord4j.discordjson.json.ApplicationCommandRequest
import discord4j.rest.interaction.GlobalCommandRegistrar
import discord4j.rest.interaction.GuildCommandRegistrar
import reactor.core.publisher.Mono

class SlashCommandManager(private val client: GatewayDiscordClient) {

    private val commands: List<SlashCommand> = listOf(
        slash("ping", "Проверка работоспособности") { "!ping" },
        slash("help", "Список команд") { "!help" },
        slashWithOption("play", "Воспроизвести трек", "query", "Ссылка или название") {
            val query = it.getOption("query").flatMap { opt -> opt.value.map { v -> v.raw } }.orElse("")
            "!play $query"
        },
        slash("stop", "Остановить воспроизведение") { "!stop" },
        slash("next", "Следующий трек") { "!next" },
        slash("queue", "Очередь треков") { "!queue" },
        slash("what", "Сейчас играет") { "!what" },
        slash("loop", "Повтор текущего трека") { "!loop" },
        slash("shuffle", "Перемешать очередь") { "!shuffle" },
        slash("playlistloop", "Повтор плейлиста") { "!playlistloop" },
        slashWithOption("jump", "Перейти к треку", "index", "Номер трека") {
            val index = it.getOption("index").flatMap { o -> o.value.map { v -> v.raw } }.orElse("")
            "!jump $index"
        },
        slashWithOption("delete", "Удалить трек из очереди", "index", "Номер трека") {
            val index = it.getOption("index").flatMap { o -> o.value.map { v -> v.raw } }.orElse("")
            "!delete $index"
        },
        slashWithOption("savefavorite", "Сохранить в избранное", "link", "Ссылка") {
            val link = it.getOption("link").flatMap { o -> o.value.map { v -> v.raw } }.orElse("")
            "!savefavorite $link"
        },
        slash("getfavorites", "Список избранного") { "!getfavorites" },
        slashWithOption("pfavorite", "Воспроизвести избранное", "index", "Номер трека") {
            val index = it.getOption("index").flatMap { o -> o.value.map { v -> v.raw } }.orElse("")
            "!pfavorite $index"
        },
        slashWithOption("rmfavorite", "Удалить из избранного", "index", "Номер трека") {
            val index = it.getOption("index").flatMap { o -> o.value.map { v -> v.raw } }.orElse("")
            "!rmfavorite $index"
        },
        slash("nowfavorite", "Добавить текущий трек в избранное") { "!nowfavorite" }
    )

    fun register() {
        val requests = commands.map { it.request }
        val rest = client.restClient
        val guildIds = client.guilds.map { it.id }.collectList().blockOptional().orElse(emptyList())
        guildIds.forEach { id ->
            GuildCommandRegistrar.create(rest, requests).registerCommands(id).blockLast()
        }
        GlobalCommandRegistrar.create(rest, requests).registerCommands().blockLast()

        client.on(ChatInputInteractionEvent::class.java)
            .flatMap { event ->
                commands.find { it.request.name() == event.commandName }?.handle(event) ?: Mono.empty()
            }
            .subscribe()
    }

    private fun slash(name: String, description: String, message: (ChatInputInteractionEvent) -> String): SlashCommand {
        val request = ApplicationCommandRequest.builder()
            .name(name)
            .description(description)
            .build()
        return RelaySlashCommand(request, message)
    }

    private fun slashWithOption(
        name: String,
        description: String,
        option: String,
        optionDescription: String,
        message: (ChatInputInteractionEvent) -> String
    ): SlashCommand {
        val optionData = discord4j.discordjson.json.ApplicationCommandOptionData.builder()
            .name(option)
            .description(optionDescription)
            .type(discord4j.core.`object`.command.ApplicationCommandOption.Type.STRING.value)
            .required(true)
            .build()
        val request = ApplicationCommandRequest.builder()
            .name(name)
            .description(description)
            .addOption(optionData)
            .build()
        return RelaySlashCommand(request, message)
    }
}
