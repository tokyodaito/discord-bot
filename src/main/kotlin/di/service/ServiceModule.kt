package di.service

import dagger.Module
import dagger.Provides
import service.GodmodeService
import service.MessageService
import service.MusicService
import service.VoiceChannelService

@Module
class ServiceModule {
    @Provides
    fun getGodmodeService(): GodmodeService {
        return GodmodeService()
    }

    @Provides
    fun getMessageService(): MessageService {
        return MessageService()
    }

    @Provides
    fun getMusicService(): MusicService {
        return MusicService()
    }

    @Provides
    fun getVoiceChannelService(): VoiceChannelService {
        return VoiceChannelService()
    }
}