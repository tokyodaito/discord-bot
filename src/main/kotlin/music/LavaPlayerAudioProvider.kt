package music

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import discord4j.voice.AudioProvider

import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame
import java.nio.ByteBuffer

class LavaPlayerAudioProvider(private val player: AudioPlayer) : AudioProvider(
    ByteBuffer.allocate(
        StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()
    )
) {
    private val frame = MutableAudioFrame()

    init {
        frame.setBuffer(buffer)
    }

    override fun provide(): Boolean {
        val didProvide = player.provide(frame)
        if (didProvide) {
            buffer.flip()
        }
        return didProvide
    }
}