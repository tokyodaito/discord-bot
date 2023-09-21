package di.database

import dagger.Module
import dagger.Provides
import model.database.Database

@Module
class DatabaseModule {
    @Provides
    fun getDatabase(): Database {
        return Database()
    }
}