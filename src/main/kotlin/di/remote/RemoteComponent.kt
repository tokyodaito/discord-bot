package di.remote

import dagger.Component
import model.remote.YouTubeImpl

@Component(modules = [RemoteModule::class])
fun interface RemoteComponent {
    fun getYouTubeImpl() : YouTubeImpl
}