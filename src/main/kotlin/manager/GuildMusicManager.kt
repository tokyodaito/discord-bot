package manager

import bot.Bot
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.common.util.Snowflake
import discord4j.voice.AudioProvider
import manager.GuildManager.playerManager
import music.LavaPlayerAudioProvider
import music.TrackScheduler

class GuildMusicManager(
    val guildId: Snowflake,
    playerManager: AudioPlayerManager
) {
    @Volatile
    var godMode = false
    internal val favorites = mutableListOf<String>()
    val player: AudioPlayer = playerManager.createPlayer()
    val scheduler = TrackScheduler(player)
    val provider: AudioProvider = LavaPlayerAudioProvider(player)

    fun addFavorites(link: String) {
        if (link.matches(Regex("^(https?|ftp)://[^\\s/$.?#].\\S*$"))) {
            favorites.add(link)
            Bot.databaseComponent.getDatabase().saveServerFavorites(guildId.toString(), favorites)
        } else {
            println("link dont enogouf link")
        }
    }

    fun playFavorite(index: Int) {
        if (index >= 0 && index <= favorites.size) {
            playerManager.loadItem(favorites[index], scheduler)
            player.addListener(scheduler)
        } else {
            println("Index off bounds")
        }
    }
}