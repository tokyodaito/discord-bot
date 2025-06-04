package di

import model.database.Database
import model.database.DatabaseImpl
import model.remote.YouTubeImpl
import service.*
import service.music.Favorites
import service.music.MusicService
import org.koin.dsl.module

fun appModule(apiKeyYouTube: String) = module {
    single { Database() }
    single { DatabaseImpl(get()) }
    single { YouTubeImpl(apiKeyYouTube) }
    single { MessageService() }
    single { VoiceChannelService() }
    single { GodmodeService(get()) }
    single { Favorites(get(), get(), get()) }
    single { MusicService(get(), get(), get(), get()) }
}
