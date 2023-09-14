package music

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import discord4j.core.event.domain.message.MessageCreateEvent
import reactor.core.publisher.Mono
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue


class TrackScheduler(
    internal val player: AudioPlayer,
    val sendInfo: (MessageCreateEvent, AudioTrack, Boolean, Boolean, Boolean) -> Mono<Void>
) :
    AudioLoadResultHandler, AudioEventAdapter() {
    private var queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()
    var loop: Boolean = false
    var currentTrack: AudioTrack? = null
    var currentEvent: MessageCreateEvent? = null

    var playlistLoop: Boolean = false
    private var initialPlaylist: List<AudioTrack> = listOf()

    override fun trackLoaded(track: AudioTrack?) {
        track?.let {
            if (player.startTrack(it, true)) {
                currentTrack = it
                currentEvent?.let { event -> sendInfo(event, track, loop, playlistLoop, false).subscribe() }
            } else if (!queue.contains(it)) {
                queue.offer(it)
                currentEvent?.let { event -> sendInfo(event, track, loop, playlistLoop, true).subscribe() }
            } else {
                println("WTF?")
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
        // LavaPlayer did not find any audio to extract
    }

    override fun loadFailed(exception: FriendlyException) {
        // LavaPlayer could not parse an audio source for some reason
    }

    override fun onTrackEnd(player: AudioPlayer?, track: AudioTrack?, endReason: AudioTrackEndReason?) {
        endReason?.let {
            if (it.mayStartNext) {
                nextTrack()
            }
        }
    }

    fun nextTrack() {
        if (playlistLoop && queue.isEmpty()) {
            queue.addAll(initialPlaylist)
        }

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