package di.database

import dagger.Component
import model.database.Database
import model.database.DatabaseImpl

@Component(modules = [DatabaseModule::class])
interface DatabaseComponent {
    fun getDatabase(): Database

    fun getDatabaseImpl(): DatabaseImpl
}