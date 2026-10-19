package dev.atomixsoft.randomizer.persistence;

import dev.atomixsoft.randomizer.model.Anime;

import java.io.IOException;
import java.util.List;

public interface AnimeLibraryRepository {

    List<Anime> load() throws IOException;
    void save(List<Anime> anime) throws IOException;

}