package di.remote

import dagger.Component
import model.remote.YouTubeImpl

@Component(modules = [RemoteModule::class])
interface RemoteComponent {
    fun getYouTubeImpl() : YouTubeImpl
}