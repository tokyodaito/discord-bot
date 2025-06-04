package bot

import bot.command.godmode.GodmodeDisableCommand
import bot.command.godmode.GodmodeEnableCommand
import bot.command.godmode.SendMessageCommand
import bot.command.help_message.HelpCommand
import bot.command.help_message.PingCommand
import bot.command.music.change_state_player.DeleteCommand
import bot.command.music.change_state_player.PlayCommand
import bot.command.music.change_state_player.PlayLinkCommand
import bot.command.music.change_state_player.StopCommand
import bot.command.music.change_state_player.favorites.*
import bot.command.music.information.QueueCommand
import bot.command.music.information.WhatPlayingCommand
import bot.command.music.loop.LoopCommand
import bot.command.music.loop.PlaylistLoopCommand
import bot.command.music.switch_tracks.JumpCommand
import bot.command.music.switch_tracks.NextTrackCommand
import bot.command.music.switch_tracks.ShuffleCommand
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient
import model.database.Database
import service.MessageService
import service.music.MusicService
import service.GodmodeService


class Bot(
    id: String,
    private val database: Database,
    private val musicService: MusicService,
    private val messageService: MessageService,
    private val godmodeService: GodmodeService,
) {
    private lateinit var clientGeneral: GatewayDiscordClient

    private val commands: MutableMap<String, Command> = HashMap()

    init {
        initDatabase()
        initCommands()
        val client: GatewayDiscordClient? = DiscordClientBuilder.create(id).build().login().block()

        if (client != null) {
            Observers(commands, messageService).setEventObserver(client)
            clientGeneral = client
            println("Bot init!")
        }

        client?.onDisconnect()?.block()
    }

    private fun initCommands() {
        commands["ping"] = PingCommand(messageService)

        commands["play"] = PlayCommand(musicService)

        commands["серега"] = PlayLinkCommand(SEREGA_PIRAT, musicService, messageService)

        commands["папочка здесь"] = GodmodeEnableCommand(godmodeService)

        commands["godmodeoff"] = GodmodeDisableCommand(godmodeService)

        commands["help"] = HelpCommand(messageService)

        commands["stop"] = StopCommand(musicService)

        commands["next"] = NextTrackCommand(messageService, musicService)

        commands["queue"] = QueueCommand(musicService)

        commands["what"] = WhatPlayingCommand(messageService)

        commands["loop"] = LoopCommand(messageService)

        commands["shuffle"] = ShuffleCommand(messageService)

        commands["playlistloop"] = PlaylistLoopCommand(messageService)

        commands["jump"] = JumpCommand(musicService)

        commands["delete"] = DeleteCommand(musicService)

        commands["savefavorite"] = SaveFavoritesCommand(musicService)

        commands["getfavorites"] = GetFavoritesCommand(musicService)

        commands["pfavorite"] = PlayFavoriteCommand(musicService)

        commands["rmfavorite"] = RemoveFromFavoriteCommand(musicService)

        commands["nowfavorite"] = NowFavoriteCommand(musicService)

        commands["sendmessage"] = SendMessageCommand(godmodeService)
    }

    

    private fun initDatabase() {
        database.initDatabase()
    }

    companion object {
        const val SEREGA_PIRAT = "https://www.youtube.com/watch?v=KhX3T_NYndo&list=PLaxxU3ZabospOFUVjRWofD-mYOQfCxpzw"

        const val prefix = "!"
    }
}