# AnimeEpisodeRandomizer

A little desktop app for those times when you want to watch an anime episode but don't feel like picking one yourself.

The idea is simple: let the app pick an episode, then go watch it however you normally would. It doesn't stream anime or open a video player.

## What it can do

- Search for anime and browse their episode lists using the [Tenrai API](https://api.tenrai.org/).
- Save anime to a personal library that stays there when you close the app.
- Display cover art and keep downloaded images cached locally.
- Pick random episode numbers from a text file without repeating a number that's already been picked from that file.
- Show selections made during the current session.

**A quick heads-up:** the online library and the randomizer aren't connected yet. You can search for and save anime, but random picks still come from a text file. Episode history from the randomizer isn't saved separately across sessions yet; the remaining numbers are saved by updating the text file itself.

## Running it

You'll need **Java 21**. There's no installer yet, so for now you can run the app from the project directory using the Gradle wrapper.

On Linux or macOS:

```sh
./gradlew run
```

On Windows:

```bat
gradlew.bat run
```

The first run will download the build dependencies. Searching the anime catalog and loading episode lists requires an internet connection.

The UI uses Swing, so on a Wayland desktop such as Hyprland it runs through XWayland.

## Using the randomizer

Create a `.txt` file with episode numbers separated by spaces or newlines, for example:

```text
1 2 3 4 5 6 7 8 9 10
```

Open that file from the **Randomizer** page and hit **Randomize episode**. After each pick, the app removes that number from the file so it won't be selected again.

**The file is modified in place**, so keep a backup if you want to reuse the original list.

## Where your data goes

Saved anime are stored in `library.json`, in the normal application-data location for your operating system:

| System | Location |
| --- | --- |
| Windows | `%APPDATA%\AnimeEpisodeRandomizer\` |
| Linux | `~/.local/share/AnimeEpisodeRandomizer/` |
| macOS | `~/Library/Application Support/AnimeEpisodeRandomizer/` |

On Linux, the app also respects `XDG_DATA_HOME` if you've set it. Cover art is kept separately in your system's cache directory (on Linux, normally `~/.cache/AnimeEpisodeRandomizer/`). Clearing the artwork cache won't remove your saved anime.

## What's next

The main thing left is getting the saved anime library and randomizer working together. The plan is to build a local episode catalog, pick episodes directly from saved anime, and keep a proper selection history so repeats are avoided even after restarting the app.

Built with Java 21, Swing, [FlatLaf](https://www.formdev.com/flatlaf/), and Gradle.