package model.database

import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Statement

class Database {
    fun initDatabase() {
        val connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
        val statement: Statement = connection.createStatement()
        statement.execute("CREATE TABLE IF NOT EXISTS server_data (guildId TEXT PRIMARY KEY, favorites TEXT)")
        connection.close()
    }

    fun saveServerFavorites(guildId: String, favorites: List<String>) {
        val connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
        val sql = "INSERT OR REPLACE INTO server_data (guildId, favorites) VALUES (?, ?)"
        val preparedStatement: PreparedStatement = connection.prepareStatement(sql)
        preparedStatement.setString(1, guildId)
        preparedStatement.setString(2, favorites.joinToString(","))
        preparedStatement.executeUpdate()
        connection.close()
    }

    fun loadServerFavorites(guildId: String): List<String>? {
        val connection = DriverManager.getConnection("jdbc:sqlite:discordBot.db")
        val sql = "SELECT favorites FROM server_data WHERE guildId = ?"
        val preparedStatement: PreparedStatement = connection.prepareStatement(sql)
        preparedStatement.setString(1, guildId)
        val resultSet = preparedStatement.executeQuery()
        return if (resultSet.next()) {
            resultSet.getString("favorites").split(",")
        } else {
            null
        }.also {
            connection.close()
        }
    }
}
