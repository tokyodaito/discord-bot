package model.database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement

class Database {
    fun initDatabase() {
        withConnection { connection ->
            val statement: Statement = connection.createStatement()
            statement.execute("CREATE TABLE IF NOT EXISTS server_data (memberId TEXT PRIMARY KEY, favorites TEXT)")
            statement.execute("CREATE TABLE IF NOT EXISTS guild_data (guildId TEXT PRIMARY KEY)")
        }
    }

    fun saveServerFavorites(memberId: String, favorite: String) {
        withConnection { connection ->
            val existingFavorites = loadServerFavorites(connection, memberId).orEmpty()
            val updatedFavorites = if (existingFavorites.isEmpty()) {
                favorite
            } else {
                existingFavorites.joinToString(",") + ",$favorite"
            }
            saveToFavoritesInDb(connection, memberId, updatedFavorites)
        }
    }

    fun loadServerFavorites(memberId: String): List<String>? {
        return try {
            withConnection { connection ->
                loadServerFavorites(connection, memberId)
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    fun addGuild(guildId: String) {
        withConnection { connection ->
            val sql = "INSERT OR REPLACE INTO guild_data (guildId) VALUES (?)"
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, guildId)
                preparedStatement.executeUpdate()
            }
        }
    }

    fun existsGuild(guildId: String): Boolean {
        return withConnection { connection ->
            val preparedStatement =
                connection.prepareStatement("SELECT 1 FROM guild_data WHERE guildId = ?")
            preparedStatement.setString(1, guildId)
            val resultSet = preparedStatement.executeQuery()
            resultSet.next()
        }
    }

    fun removeServerFavorite(memberId: String, favoriteToRemove: String) {
        withConnection { connection ->
            val existingFavorites = loadServerFavorites(connection, memberId).orEmpty().toMutableList()
            existingFavorites.remove(favoriteToRemove)
            updateServerFavorites(connection, memberId, existingFavorites.joinToString(","))
        }
    }

    private fun <T> withConnection(action: (Connection) -> T): T {
        DriverManager.getConnection(DATABASE).use { connection ->
            return action(connection)
        }
    }

    private fun saveToFavoritesInDb(connection: Connection, memberId: String, favorites: String) {
        val sql = "INSERT OR REPLACE INTO server_data (memberId, favorites) VALUES (?, ?)"
        connection.prepareStatement(sql).use { preparedStatement ->
            preparedStatement.setString(1, memberId)
            preparedStatement.setString(2, favorites)
            preparedStatement.executeUpdate()
        }
    }

    private fun loadServerFavorites(connection: Connection, memberId: String): List<String>? {
        val sql = "SELECT favorites FROM server_data WHERE memberId = ?"
        connection.prepareStatement(sql).use { preparedStatement ->
            preparedStatement.setString(1, memberId)
            val resultSet = preparedStatement.executeQuery()
            if (!resultSet.next()) return null
            val favorites = resultSet.getString("favorites").orEmpty()
            if (favorites.isBlank()) return emptyList()
            return favorites.split(",").filter { it.isNotBlank() }
        }
    }

    private fun updateServerFavorites(connection: Connection, memberId: String, updatedFavorites: String) {
        val sql = "INSERT OR REPLACE INTO server_data (memberId, favorites) VALUES (?, ?)"
        connection.prepareStatement(sql).use { preparedStatement ->
            preparedStatement.setString(1, memberId)
            preparedStatement.setString(2, updatedFavorites)
            preparedStatement.executeUpdate()
        }
    }

    companion object {
        private const val DATABASE = "jdbc:sqlite:discordBot.db"
    }
}
