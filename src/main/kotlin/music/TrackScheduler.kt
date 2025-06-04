package music

import bot.Bot
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.event.domain.message.MessageCreateEvent
import discord4j.core.`object`.entity.Message
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.properties.Delegates

class TrackScheduler(
    private val player: AudioPlayer,
) : AudioLoadResultHandler, AudioEventAdapter() {
    var loop: Boolean = false
    var playlistLoop: Boolean by Delegates.observable(false) { _, _, _ ->
        if (currentTrack != null)
            initialPlaylist.add(currentTrack!!)
        initialPlaylist.addAll(queue.toList())
    }

    var currentTrack: AudioTrack? = null
    var currentEvent: MessageCreateEvent? = null

    var lastMessage: Message? = null
    val lastMessageLock = Any()

    private var firstSong: Boolean = true
    private val messageService = Bot.serviceComponent.getMessageService()
    private var queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()
    private var initialPlaylist: MutableList<AudioTrack> = mutableListOf()

    fun nextTrack() {
        println("nextTrack")
        if (!loop) {
            currentTrack = queue.poll()
        }
        currentTrack?.let { playTrack(it) }
    }

    fun getFullTrackList(): List<AudioTrack> {
        val fullTrackList = mutableListOf<AudioTrack>()
        return if (playlistLoop) {
            initialPlaylist
        } else {
            currentTrack?.let { fullTrackList.add(it) }
            fullTrackList.addAll(queue)
            fullTrackList
        }
    }

    fun deleteTrack(index: Int): Boolean {
        if (index == 1) {
            nextTrack()
            return true
        }

        val adjustedIndex = index - 2
        if (adjustedIndex >= 0 && adjustedIndex < queue.size) {
            val list = ArrayList(queue)
            list.removeAt(adjustedIndex)
            queue = LinkedBlockingQueue(list)
            return true
        }

        println("Index out of bounds")
        return false
    }


    fun jumpToTrack(index: Int): Boolean {
        val trackList = getFullTrackList()

        if (index > 1 && index <= trackList.size) {
            playTrackByIndex(index, trackList)
            return true
        }
        return false
    }

    fun shuffleQueue() {
        val shuffledList: List<AudioTrack> = queue.shuffled()
        queue = LinkedBlockingQueue()
        queue.addAll(shuffledList)
    }

    fun clearQueue() {
        currentTrack = null
        firstSong = true
        loop = false
        playlistLoop = false
        player.stopTrack()
        queue.clear()
    }

    private fun playTrackByIndex(index: Int, trackList: List<AudioTrack>) {
        val track = trackList[index - 1]
        currentTrack = track
        player.startTrack(track, false)
    }

    private fun playTrack(track: AudioTrack) {
        val trackToPlay = getTrackToPlay(track)
        player.startTrack(trackToPlay, false)
        sendTrackInfoMessage(track)
    }

    private fun getTrackToPlay(track: AudioTrack): AudioTrack {
        return if (loop) track.makeClone() else track
    }

    private fun sendTrackInfoMessage(track: AudioTrack) {
        currentEvent?.let { event ->
            if (firstSong) {
                firstSong = false
                messageService.sendNewInformationAboutSong(event, track, loop, playlistLoop, false).subscribe()
            } else {
                messageService.sendInformationAboutSong(event, track, loop, playlistLoop, false).subscribe()
            }
        }
    }

    private fun handleTrack(track: AudioTrack, event: MessageCreateEvent) {
        if (player.startTrack(track, true)) {
            handleFirstTrack(track, event)
        } else if (!queue.contains(track)) {
            addTrackToQueue(track, event)
        } else {
            println("Track is already in the queue")
        }
    }

    private fun handleFirstTrack(track: AudioTrack, event: MessageCreateEvent) {
        currentTrack = track
        if (firstSong) {
            messageService.sendNewInformationAboutSong(event, track, loop, playlistLoop, false).subscribe()
        }
    }

    private fun addTrackToQueue(track: AudioTrack, event: MessageCreateEvent) {
        if (queue.offer(track)) {
            messageService.sendInformationAboutSong(event, track, loop, playlistLoop, true).subscribe()
        } else {
            messageService.createEmbedMessage(event, "Не удалось добавить в очередь").subscribe()
        }
    }

    private fun shouldRestartTrack(reason: AudioTrackEndReason): Boolean {
        return (reason == AudioTrackEndReason.FINISHED && loop) || (queue.isNotEmpty() && reason == AudioTrackEndReason.FINISHED)
    }

    private fun shouldAddPlaylistBack(reason: AudioTrackEndReason): Boolean {
        return playlistLoop && (reason == AudioTrackEndReason.CLEANUP || queue.isEmpty())
    }

    private fun shouldClearPlayer(reason: AudioTrackEndReason): Boolean {
        return reason == AudioTrackEndReason.CLEANUP || reason == AudioTrackEndReason.STOPPED || queue.isEmpty()
    }

    private fun handleClearPlayer(player: AudioPlayer?) {
        currentTrack = null
        Mono.delay(Duration.ofMinutes(5)).subscribe {
            if (queue.isEmpty() && currentTrack == null) {
                resetPlayerState()
                currentEvent?.let { event ->
                    clearQueue()
                    player?.removeListener(this)
                    Bot.serviceComponent.getVoiceChannelService().disconnect(event).subscribe()
                }
            }
        }
    }

    private fun resetPlayerState() {
        loop = false
        firstSong = true
    }

    override fun trackLoaded(track: AudioTrack?) {
        val event = currentEvent ?: run {
            println("Current event is null")
            return
        }

        track?.let {
            handleTrack(it, event)
        }
    }


    override fun playlistLoaded(playlist: AudioPlaylist?) {
        playlist?.tracks?.let {
            queue.addAll(it)
        }
        if (currentTrack == null)
            nextTrack()
    }

    override fun noMatches() {
        println("No matches found for the given input")
        currentEvent?.let {
            messageService.createEmbedMessage(it, "Ошибка загрузки трека")
                .subscribe()
        }
    }

    override fun loadFailed(exception: FriendlyException) {
        println("Failed to load track: ${exception.message}")
        currentEvent?.let {
            messageService.createEmbedMessage(it, "Ошибка загрузки трека")
                .subscribe()
        }
    }

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        if (endReason?.mayStartNext != true) return

        when {
            shouldRestartTrack(endReason) -> nextTrack()
            shouldAddPlaylistBack(endReason) -> {
                queue.addAll(initialPlaylist)
                nextTrack()
            }

            shouldClearPlayer(endReason) -> handleClearPlayer(player)
            else -> println("EndReason: $endReason")
        }
    }
}
