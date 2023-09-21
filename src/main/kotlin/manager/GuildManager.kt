package manager

import bot.Bot
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.common.util.Snowflake
import discord4j.core.event.domain.message.MessageCreateEvent

object GuildManager {
    val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        configuration.frameBufferFactory = AudioFrameBufferFactory { bufferDuration, format, stopping ->
            NonAllocatingAudioFrameBuffer(bufferDuration, format, stopping)
        }
        AudioSourceManagers.registerRemoteSources(this)
    }

    private val musicManagers: MutableMap<Snowflake, GuildMusicManager> = mutableMapOf()

    fun getGuildMusicManager(guildId: Snowflake): GuildMusicManager {
        return musicManagers.computeIfAbsent(guildId) {
            GuildMusicManager(guildId, playerManager).apply {
                Bot.databaseComponent.getDatabase().loadServerFavorites(guildId.toString())
                    ?.let { linkList -> favorites.addAll(linkList) }
            }
        }
    }

    fun getGuildMusicManager(event: MessageCreateEvent): GuildMusicManager {
        val guildId = event.guildId.orElse(null)
        return musicManagers.computeIfAbsent(guildId) {
            GuildMusicManager(guildId, playerManager).apply {
                Bot.databaseComponent.getDatabase().loadServerFavorites(guildId.toString())
                    ?.let { linkList -> favorites.addAll(linkList) }
            }
        }
    }
}