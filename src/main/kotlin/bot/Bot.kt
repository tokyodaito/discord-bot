package bot

import bot.command.godmode.GodmodeDisableCommand
import bot.command.godmode.GodmodeEnableCommand
import bot.command.help_message.HelpCommand
import bot.command.help_message.PingCommand
import bot.command.music.change_state_player.*
import bot.command.music.information.QueueCommand
import bot.command.music.information.WhatPlayingCommand
import bot.command.music.loop.LoopCommand
import bot.command.music.loop.PlaylistLoopCommand
import bot.command.music.switch_tracks.JumpCommand
import bot.command.music.switch_tracks.NextTrackCommand
import bot.command.music.switch_tracks.ShuffleCommand
import di.database.DaggerDatabaseComponent
import di.database.DatabaseComponent
import di.remote.DaggerRemoteComponent
import di.remote.RemoteComponent
import di.remote.RemoteModule
import di.service.DaggerServiceComponent
import di.service.ServiceComponent
import discord4j.core.DiscordClientBuilder
import discord4j.core.GatewayDiscordClient


class Bot(id: String, private val apiKeyYouTube: String) {
    private lateinit var clientGeneral: GatewayDiscordClient

    private val commands: MutableMap<String, Command> = HashMap()

    init {
        initDaggerComponents()
        initCommands()
        val client: GatewayDiscordClient? = DiscordClientBuilder.create(id).build().login().block()

        if (client != null) {
            Observers(commands).setEventObserver(client)
            clientGeneral = client
            println("Bot init!")
        }

        client?.onDisconnect()?.block()
    }

    private fun initCommands() {
        commands["ping"] = PingCommand()

        commands["play"] = PlayCommand()

        commands["серега"] = PlayLinkCommand(SEREGA_PIRAT)

        commands["папочка здесь"] = GodmodeEnableCommand()

        commands["godmodeoff"] = GodmodeDisableCommand()

        commands["help"] = HelpCommand()

        commands["stop"] = StopCommand()

        commands["next"] = NextTrackCommand()

        commands["queue"] = QueueCommand()

        commands["what"] = WhatPlayingCommand()

        commands["loop"] = LoopCommand()

        commands["shuffle"] = ShuffleCommand()

        commands["playlistloop"] = PlaylistLoopCommand()

        commands["jump"] = JumpCommand()

        commands["delete"] = DeleteCommand()
    }

    private fun initDaggerComponents() {
        serviceComponent = DaggerServiceComponent.builder().build()
        remoteComponent = DaggerRemoteComponent.builder().remoteModule(RemoteModule(apiKeyYouTube)).build()
        databaseComponent = DaggerDatabaseComponent.builder().build()
    }

    private fun initDatabase() {
        databaseComponent.getDatabase().initDatabase()
    }

    companion object {
        lateinit var serviceComponent: ServiceComponent
            private set

        lateinit var remoteComponent: RemoteComponent
            private set

        lateinit var databaseComponent: DatabaseComponent
            private set

        const val SEREGA_PIRAT = "https://www.youtube.com/watch?v=KhX3T_NYndo&list=PLaxxU3ZabospOFUVjRWofD-mYOQfCxpzw"

        const val prefix = "!"
    }
}