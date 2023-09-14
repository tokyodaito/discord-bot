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


class TrackScheduler(private val player: AudioPlayer, val sendInfo: (MessageCreateEvent, AudioTrack) -> Mono<Void>) :
    AudioLoadResultHandler, AudioEventAdapter() {
    private var queue: BlockingQueue<AudioTrack> = LinkedBlockingQueue()
    var loop: Boolean = false
    var currentTrack: AudioTrack? = null
    var currentEvent: MessageCreateEvent? = null

    override fun trackLoaded(track: AudioTrack?) {
        track?.let {
            if (player.startTrack(it, true)) {
                currentTrack = it
            } else if (!queue.contains(it)) {
                queue.offer(it)
            }
            currentEvent?.let { event -> sendInfo(event, track).subscribe() }
        }
    }

    override fun playlistLoaded(playlist: AudioPlaylist?) {
        playlist?.tracks?.let { queue.addAll(it) }
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
        if (!loop) {
            currentTrack = queue.poll()
            currentTrack?.let { track ->
                player.startTrack(track, false)
            }
        } else {
            currentTrack?.let { player.startTrack(it.makeClone(), false) }
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