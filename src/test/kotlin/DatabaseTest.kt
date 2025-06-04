import model.database.Database
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseTest {
    @Test
    fun `add and check guild`() {
        val db = Database()
        db.initDatabase()
        val guildId = "testGuild"
        db.addGuild(guildId)
        assertTrue(db.existsGuild(guildId))
    }
}
