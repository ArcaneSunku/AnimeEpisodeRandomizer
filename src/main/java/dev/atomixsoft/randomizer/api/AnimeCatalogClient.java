package dev.atomixsoft.randomizer.api;

import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.Page;

import java.io.IOException;

public interface AnimeCatalogClient {

    default void invalidateEpisodeCache(int animeId) {}

    Page<Anime> searchAnime(String query, int page) throws IOException, InterruptedException;
    Page<AnimeEpisode> getEpisodes(int animeId, int page) throws IOException, InterruptedException;

}
