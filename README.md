# AnimeEpisodeRandomizer

A little desktop app for when you want to watch an anime episode but don't feel like choosing one yourself.

Pick an anime you've saved, hit **Randomize episode**, and watch the result however you normally would. The app doesn't stream anime or open a video player.

## What it does

- Search for anime and browse episode lists through the [Tenrai API](https://api.tenrai.org/).
- Save anime to your own library, with cover art cached locally.
- Cache complete episode lists and browse saved episodes offline.
- Randomly pick an episode of **one chosen anime**, by default.
- Optionally choose a group of anime and randomize across the group, either evenly by anime or evenly by episode.
- Exclude filler or recap episodes, if you want.
- Record every successful selection on disk so it won't be picked again after restarting.
- Show selections made during the current run in Session Activity.

The app doesn't automatically reset an exhausted episode pool. History browsing, undo, and reset controls are planned for a later update.

## Running it

You'll need **Java 21**. There isn't a packaged installer yet, so run the app with the Gradle wrapper from the project directory.

Linux or macOS:

```sh
./gradlew run
```

Windows:

```bat
gradlew.bat run
```

The first run downloads the build dependencies. Anime searches and catalog updates require an internet connection; previously cached episodes can be used offline. On Wayland desktops such as Hyprland, Swing runs through XWayland.

## Picking an episode

1. Open **Anime Library**, search for a series, and add it to Saved.
2. Open its episode list to populate the local catalog, or use **Refresh episode list** in Saved.
3. Go to **Randomizer** and choose the anime.
4. Adjust the filler/recap options if you'd like, and click **Randomize episode**.

The result is saved before it appears. If you've selected every eligible episode, the app tells you instead of repeating an earlier pick. To choose from multiple anime, switch to **Selected group** and select the shows you want to include.

## Where your data goes

Saved anime and selections live in the normal application-data directory for your OS:

| System | Location |
| --- | --- |
| Windows | `%APPDATA%\AnimeEpisodeRandomizer\` |
| Linux | `~/.local/share/AnimeEpisodeRandomizer/` |
| macOS | `~/Library/Application Support/AnimeEpisodeRandomizer/` |

The app stores your saved collection in `library.json` and its selection history in `history.json`. On Linux, it respects `XDG_DATA_HOME` when configured.

Artwork and episode catalogs are cached separately in the system's cache directory (on Linux, usually `~/.cache/AnimeEpisodeRandomizer/`). You can clear cached data without losing your saved library or selection history, although the episode catalogs must then be downloaded again.

Built with Java 21, Swing, [FlatLaf](https://www.formdev.com/flatlaf/), Jackson, and Gradle.
