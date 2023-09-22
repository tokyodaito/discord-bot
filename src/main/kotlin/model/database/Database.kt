package model.database

import java.sql.*

class Database {
    fun initDatabase() {
        val connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
        val statement: Statement = connection.createStatement()
        statement.execute("CREATE TABLE IF NOT EXISTS server_data (memberId TEXT PRIMARY KEY, favorites TEXT)")
        connection.close()
    }

    fun saveServerFavorites(memberId: String, favorite: String) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
            val sql = "INSERT OR REPLACE INTO server_data (memberId, favorites) VALUES (?, ?)"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, memberId)

            val existingFavorites = loadServerFavorites(memberId)
            val updatedFavorites = if (existingFavorites.isNullOrEmpty()) {
                favorite
            } else {
                existingFavorites.joinToString(",") + ",$favorite"
            }

            preparedStatement.setString(2, updatedFavorites)
            preparedStatement.executeUpdate()
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }

    fun loadServerFavorites(memberId: String): List<String>? {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
            val sql = "SELECT favorites FROM server_data WHERE memberId = ?"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, memberId)

            val resultSet = preparedStatement.executeQuery()
            if (resultSet.next()) {
                return resultSet.getString("favorites").split(",")
            }
        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
        return null
    }

    fun removeServerFavorite(memberId: String, favoriteToRemove: String) {
        var connection: Connection? = null
        var preparedStatement: PreparedStatement? = null

        try {
            connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")

            val existingFavorites = loadServerFavorites(memberId)?.toMutableList() ?: mutableListOf()

            existingFavorites.remove(favoriteToRemove)

            val updatedFavorites = existingFavorites.joinToString(",")

            val sql = "INSERT OR REPLACE INTO server_data (memberId, favorites) VALUES (?, ?)"
            preparedStatement = connection.prepareStatement(sql)
            preparedStatement.setString(1, memberId)
            preparedStatement.setString(2, updatedFavorites)
            preparedStatement.executeUpdate()

        } catch (e: SQLException) {
            e.printStackTrace()
        } finally {
            preparedStatement?.close()
            connection?.close()
        }
    }
}
