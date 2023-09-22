package di.database

import dagger.Module
import dagger.Provides
import model.database.Database
import model.database.DatabaseImpl

@Module
class DatabaseModule {
    @Provides
    fun getDatabase(): Database {
        return Database()
    }

    @Provides
    fun getDatabaseImpl(): DatabaseImpl {
        return DatabaseImpl()
    }
}