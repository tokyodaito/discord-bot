import di.remote.RemoteModule
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteModuleTest {
    @Test
    fun `provided api key is stored in YouTubeImpl`() {
        val key = "TEST_KEY"
        val module = RemoteModule(key)
        val impl = module.getYouTubeImpl()
        val field = impl.javaClass.getDeclaredField("apiKey")
        field.isAccessible = true
        val value = field.get(impl) as String
        assertEquals(key, value)
    }
}
