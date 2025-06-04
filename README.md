# Discord Bot

Этот проект представляет собой Discord-бота, написанного на Kotlin. Для сборки и запуска используется Gradle Wrapper.

## Требования
- Java 17 или новее
- Доступ в интернет для загрузки зависимостей Gradle

## Установка и сборка
1. Убедитесь, что установлен JDK 17 или новее:
   ```bash
   java -version
   ```
2. Запустите скрипт `setup.sh`, который сделает файл `gradlew` исполнимым и выполнит сборку:
   ```bash
   ./setup.sh
   ```
   После выполнения в каталоге `build/libs` появится готовый jar-файл.

## Запуск бота
После сборки запустить бота можно так:
```bash
./gradlew run --args "<DISCORD_TOKEN> <YOUTUBE_API_KEY>"
```
где вместо `<DISCORD_TOKEN>` и `<YOUTUBE_API_KEY>` нужно подставить токен вашего бота и ключ YouTube API соответственно.

Также можно запустить jar-файл напрямую:
```bash
java -jar build/libs/discord-bot-1.0-SNAPSHOT.jar <DISCORD_TOKEN> <YOUTUBE_API_KEY>
```
