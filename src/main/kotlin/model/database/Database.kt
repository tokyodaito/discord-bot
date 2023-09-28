package model.database

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Statement

class Database {
    fun initDatabase() {
        withConnection {
            connection ->
            val statement: Statement = connection.createStatement()
            statement.execute("CREATE TABLE IF NOT EXISTS server_data (memberId TEXT PRIMARY KEY, favorites TEXT)")
            connection.close()
        }
    }

    fun saveServerFavorites(memberId: String, favorite: String) {
        val updatedFavorites = calculateUpdatedFavorites(memberId, favorite)
        withConnection { connection ->
            saveToFavoritesInDb(connection, memberId, updatedFavorites)
        }
    }

    fun loadServerFavorites(memberId: String): List<String>? {
        return try {
            withConnection { connection ->
                executeQuery(connection, memberId)
            }
        } catch (e: SQLException) {
            e.printStackTrace()
            null
        }
    }

    fun removeServerFavorite(memberId: String, favoriteToRemove: String) {
        val existingFavorites = loadServerFavorites(memberId)?.toMutableList() ?: mutableListOf()
        existingFavorites.remove(favoriteToRemove)
        val updatedFavorites = existingFavorites.joinToString(",")
        updateServerFavorites(memberId, updatedFavorites)
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

    private fun calculateUpdatedFavorites(memberId: String, favorite: String): String {
        val existingFavorites = loadServerFavorites(memberId)
        return if (existingFavorites.isNullOrEmpty()) {
            favorite
        } else {
            existingFavorites.joinToString(",") + ",$favorite"
        }
    }

    private fun executeQuery(connection: Connection, memberId: String): List<String>? {
        val sql = "SELECT favorites FROM server_data WHERE memberId = ?"
        connection.prepareStatement(sql).use { preparedStatement ->
            preparedStatement.setString(1, memberId)
            val resultSet = preparedStatement.executeQuery()
            return if (resultSet.next()) resultSet.getString("favorites").split(",") else null
        }
    }

    private fun updateServerFavorites(memberId: String, updatedFavorites: String) {
        withConnection { connection ->
            val sql = "INSERT OR REPLACE INTO server_data (memberId, favorites) VALUES (?, ?)"
            connection.prepareStatement(sql).use { preparedStatement ->
                preparedStatement.setString(1, memberId)
                preparedStatement.setString(2, updatedFavorites)
                preparedStatement.executeUpdate()
            }
        }
    }

    companion object {
        private const val DATABASE = "jdbc:sqlite:discordBot.db"
    }
}
