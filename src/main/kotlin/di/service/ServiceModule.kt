package di.service

import dagger.Module
import dagger.Provides
import service.GodmodeService
import service.MessageService
import service.music.MusicService
import service.VoiceChannelService
import service.AnalyticsService

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

    @Provides
    fun getAnalyticsService(): AnalyticsService {
        return AnalyticsService()
    }
}