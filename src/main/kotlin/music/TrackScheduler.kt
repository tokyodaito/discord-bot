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
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class TrackScheduler(
    private val player: AudioPlayer,
) : AudioLoadResultHandler, AudioEventAdapter() {
    private val messageService = Bot.serviceComponent.getMessageService()

    private var queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()

    var loop: Boolean = false

    var currentTrack: AudioTrack? = null

    var currentEvent: MessageCreateEvent? = null

    var playlistLoop: Boolean = false

    var firstSong: Boolean = true

    var lastMessage: Message? = null
    val lastMessageLock = Any()

    private var initialPlaylist: List<AudioTrack> = listOf()

    override fun trackLoaded(track: AudioTrack?) {
        track?.let { trackNotNull ->
            val event = currentEvent
            if (event == null) {
                println("Current event is null")
                return
            }

            when {
                player.startTrack(trackNotNull, true) -> {
                    currentTrack = trackNotNull
                    if (firstSong) {
                        messageService.sendInformationAboutSong(event, trackNotNull, loop, playlistLoop, false)
                            .subscribe()
                    } else {
                        return
                    }
                }

                !queue.contains(trackNotNull) -> {
                    queue.offer(trackNotNull)
                    messageService.sendInformationAboutSong(event, trackNotNull, loop, playlistLoop, true).subscribe()
                }

                else -> {
                    println("Track is already in the queue")
                }
            }
        }
    }


    override fun playlistLoaded(playlist: AudioPlaylist?) {
        playlist?.tracks?.let {
            queue.addAll(it)
            initialPlaylist = it.toList()
        }
        if (currentTrack == null)
            nextTrack()
    }

    override fun noMatches() {
        println("No matches found for the given input")
        currentEvent?.let {
            Bot.serviceComponent.getVoiceChannelService()
                .disconnect(it)
                .then(messageService.sendMessage(it, "Ошибка загрузки трека"))
                .subscribe()
        }
        clearQueue()
    }

    override fun loadFailed(exception: FriendlyException) {
        println("Failed to load track: ${exception.message}")
        currentEvent?.let {
            Bot.serviceComponent.getVoiceChannelService()
                .disconnect(it)
                .then(messageService.sendMessage(it, "Ошибка загрузки трека"))
                .subscribe()
        }
        clearQueue()
    }

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        endReason?.takeIf { it.mayStartNext }?.let {
            when {
                queue.isNotEmpty() -> {
                    nextTrack()
                }

                currentTrack != null && loop -> {
                    nextTrack()
                }

                playlistLoop -> {
                    queue.addAll(initialPlaylist)
                    nextTrack()
                }

                else -> {
                    loop = false
                    firstSong = true
                }
            }
        }
    }

    fun nextTrack() {
        println("nextTrack")
        if (!loop) {
            currentTrack = queue.poll()
        }
        currentTrack?.let { track ->
            val trackToPlay = if (loop) track.makeClone() else track
            player.startTrack(trackToPlay, false)
            currentEvent?.let { event ->
                messageService.sendInformationAboutSong(event, track, loop, playlistLoop, false).subscribe()
            }
        }
    }


    fun getFullTrackList(): List<AudioTrack> {
        val fullTrackList = mutableListOf<AudioTrack>()
        currentTrack?.let { fullTrackList.add(it) }
        fullTrackList.addAll(queue)
        return fullTrackList
    }

    fun deleteTrack(index: Int) {
        if (index >= 0 && index < queue.size) {
            val list = ArrayList(queue)
            list.removeAt(index)
            queue = LinkedBlockingQueue(list)
        } else {
            println("Index out of bounds")
        }
    }

    fun jumpToTrack(index: Int): Boolean {
        val trackList = getFullTrackList()
        return if (index > 1 && index <= trackList.size) {
            val track = trackList[index - 1]
            currentTrack = track
            player.startTrack(track, false)
            true
        } else {
            false
        }
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
}