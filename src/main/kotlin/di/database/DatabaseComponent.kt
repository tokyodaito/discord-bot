package di.database

import dagger.Component
import model.database.Database

@Component(modules = [DatabaseModule::class])
interface DatabaseComponent {
    fun getDatabase(): Database
}