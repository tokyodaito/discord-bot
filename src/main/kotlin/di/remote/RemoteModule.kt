package di.remote

import dagger.Module
import dagger.Provides
import remote.YouTubeImpl

@Module
class RemoteModule(private val apiKeyYouTube: String) {
    @Provides
    fun getYouTubeImpl(): YouTubeImpl {
        return YouTubeImpl(apiKeyYouTube)
    }
}