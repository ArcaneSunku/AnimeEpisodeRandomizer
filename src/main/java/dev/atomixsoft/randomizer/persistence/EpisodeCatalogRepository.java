package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.EpisodeCatalog;

import java.io.IOException;
import java.util.Optional;

public interface EpisodeCatalogRepository {

    Optional<EpisodeCatalog> load(int animeId) throws IOException;
    void save(EpisodeCatalog catalog) throws IOException;

}