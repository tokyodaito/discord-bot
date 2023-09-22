package di.service

import dagger.Component
import service.GodmodeService
import service.MessageService
import service.VoiceChannelService
import service.music.MusicService

@Component(modules = [ServiceModule::class])
interface ServiceComponent {
    fun getGodmodeService(): GodmodeService
    fun getMessageService(): MessageService
    fun getMusicService(): MusicService
    fun getVoiceChannelService(): VoiceChannelService
}