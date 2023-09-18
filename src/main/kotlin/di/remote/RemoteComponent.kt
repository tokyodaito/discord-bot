package di.remote

import dagger.Component
import remote.YouTubeImpl

@Component(modules = [RemoteModule::class])
interface RemoteComponent {
    fun getYouTubeImpl() : YouTubeImpl
}