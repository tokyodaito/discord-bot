package music

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
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class TrackScheduler(
    internal val player: AudioPlayer,
    val sendInfo: (MessageCreateEvent, AudioTrack, Boolean, Boolean, Boolean) -> Mono<Void>
) : AudioLoadResultHandler, AudioEventAdapter() {

    private var queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()

    @Volatile
    var loop: Boolean = false

    @Volatile
    var currentTrack: AudioTrack? = null

    var currentEvent: MessageCreateEvent? = null

    @Volatile
    private var firstSong = true

    @Volatile
    var playlistLoop: Boolean = false

    @Volatile
    var lastMessage: Message? = null

    private var initialPlaylist: List<AudioTrack> = listOf()

    override fun trackLoaded(track: AudioTrack?) {
        track?.let {
            if (player.startTrack(it, true)) {
                currentTrack = it
                if (firstSong) {
                    currentEvent?.let { event ->
                        sendInfo(event, track, loop, playlistLoop, false).subscribe()
                    } ?: println("Current event is null")
                } else {
                    print("WTF??")
                }
            } else if (!queue.contains(it)) {
                queue.offer(it)
                currentEvent?.let { event -> sendInfo(event, track, loop, playlistLoop, true).subscribe() }
            } else {
                println("Track is already in the queue")
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
    }

    override fun loadFailed(exception: FriendlyException) {
        println("Failed to load track: ${exception.message}")
    }

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        endReason?.let {
            if (it.mayStartNext) {
                firstSong = false
                nextTrack()
            } else {
                firstSong = true
                loop = false
                playlistLoop = false

                if (playlistLoop && queue.isEmpty()) {
                    queue.addAll(initialPlaylist)
                    nextTrack()
                }
            }
        }
    }

    fun nextTrack() {
        if (!loop) {
            currentTrack = queue.poll()
            currentTrack?.let { track ->
                player.startTrack(track, false)
                currentEvent?.let { event -> sendInfo(event, track, loop, playlistLoop, false).subscribe() }
            }
        } else {
            currentTrack?.let { track ->
                player.startTrack(track.makeClone(), false)
                currentEvent?.let { event -> sendInfo(event, track, loop, playlistLoop, false).subscribe() }
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


    fun shuffleQueue() {
        val shuffledList: List<AudioTrack> = queue.shuffled()
        queue = LinkedBlockingQueue()
        queue.addAll(shuffledList)
    }

    fun clearQueue() {
        player.stopTrack()
        currentTrack = null
        queue.clear()
    }
}