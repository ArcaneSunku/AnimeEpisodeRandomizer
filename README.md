# AnimeEpisodeRandomizer

AnimeEpisodeRandomizer is a desktop app for picking an episode when you know which anime you want to watch but can't decide where to start. Choose a series from your saved library, hit **Randomize episode**, and the app picks one you haven't been assigned before.

It doesn't play video, open streaming sites, or keep track of what you've watched. It only makes the choice; how and where you watch is up to you.

## Features

- Find anime and browse episode titles with the [Tenrai API](https://api.tenrai.org/).
- Save your own anime library, complete with cover art.
- Download episode catalogs once and use them for random picks without an internet connection.
- Randomize a single selected anime by default, or explicitly choose a group of series.
- For group selection, give every episode equal odds or give every anime equal odds.
- Optionally leave out filler episodes and recaps.
- Keep a persistent record of selections so episodes don't repeat across launches.
- Organize selections into sessions; undo or reset picks without deleting their history.
- Adjust default filters, group weighting, and the dark/light appearance in Settings.

An exhausted series stays exhausted until you undo or reset selections. Starting another session doesn't automatically make old picks eligible again.

## Getting started

The simplest way to run the source project is with **JDK 21** and the included Gradle wrapper. From the project directory:

```sh
./gradlew run
```

On Windows, use `gradlew.bat run`. Gradle downloads the dependencies on the first build.

Once the app is running:

1. Search for an anime in **Anime Library** and add it to **Saved**.
2. Select the saved series and open its episodes. The app downloads and caches the catalog. You can also use **Refresh episode list** to request fresh data.
3. Open **Randomizer**, select one anime, and click **Randomize episode**.
4. See your previous picks in **History & Sessions**. You can undo selections or reset a series there.

Use **Ctrl+1** through **Ctrl+4** (or **Command+1** through **Command+4** where appropriate) to switch between the four application pages.

Want a surprise across several shows? Switch the randomizer to **Selected group**, choose the series, and pick the weighting you prefer. Single-anime selection remains the default.

Internet access is needed to search and download new catalogs. Cached catalogs and saved history are available offline. Java Swing uses XWayland on Wayland desktops such as Hyprland; make sure XWayland is enabled.

## Settings and local files

The program stores durable information separately from replaceable downloads. You can find both locations on the **Settings** page.

| System | Application data | Cache |
| --- | --- | --- |
| Windows | `%APPDATA%\AnimeEpisodeRandomizer\` | `%LOCALAPPDATA%\AnimeEpisodeRandomizer\` |
| Linux | `~/.local/share/AnimeEpisodeRandomizer/` | `~/.cache/AnimeEpisodeRandomizer/` |
| macOS | `~/Library/Application Support/AnimeEpisodeRandomizer/` | `~/Library/Caches/AnimeEpisodeRandomizer/` |

On Linux, the app respects `XDG_DATA_HOME` and `XDG_CACHE_HOME` when set to absolute paths.

Application data includes `library.json`, `history.json`, and `settings.json`. The history repository also retains a `.bak` backup that can be restored from the history page. **Back up the application-data directory before moving machines or reinstalling your OS.** The cache contains artwork and episode catalogs and can be regenerated from the API.

The Settings page lets you remove either cache type. Clearing episode catalogs temporarily disables offline episode browsing and randomization for those series until their catalogs have been downloaded again. If you change the appearance, restart the app to apply it. If the preferences file becomes unreadable, the application offers to start with defaults without immediately overwriting the damaged file.

## Building and packaging

To run the full test suite:

```sh
./gradlew clean test
```

To create a Java application distribution with launcher scripts and bundled dependencies:

```sh
./gradlew installDist
./gradlew distZip
```

These Gradle distributions require Java 21 on the user's system. For a bundled Java runtime, use the native packaging scripts from **JDK 21**:

**Linux:**

```sh
./scripts/package-linux.sh
```

**Windows (PowerShell):**

```powershell
.\scripts\package-windows.ps1
```

The default is a platform-specific `app-image`, written under `build/native/`. On Linux you can pass `deb` or `rpm` to the script; on Windows you can pass `exe` or `msi`. Installer formats may require additional OS packaging tools (such as WiX on Windows). Native packages must be built on their target OS, not cross-compiled.

The native images include a Java runtime, so an end user doesn't need to install Java separately. Test the resulting launcher on your target distribution or Windows version before releasing it. Packages are not digitally signed.

## A note on episode data

Anime titles and episode details come from a third-party service. Availability and accuracy depend on the underlying catalog. Complete cached snapshots mean the app retrieved every page reported by the API; they don't guarantee the provider has every episode ever released. Previously recorded selections remain in your history even if you remove a series from the library or clear its cached catalog.

## Technology

Java 21, Swing, [FlatLaf](https://www.formdev.com/flatlaf/), Jackson, Gradle, and the Tenrai API.
