package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.persistence.AnimeLibraryRepository;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public final class AnimeLibraryService {

    private final AnimeLibraryRepository m_Repository;
    private final LinkedHashMap<Integer, Anime> m_Anime = new LinkedHashMap<>();
    private boolean m_Loaded;

    public AnimeLibraryService(AnimeLibraryRepository repository) {
        m_Repository = Objects.requireNonNull(repository);
    }

    public synchronized List<Anime> load() throws IOException {
        m_Loaded = false;

        List<Anime> entries = m_Repository.load();
        LinkedHashMap<Integer, Anime> loaded = new LinkedHashMap<>();

        for (Anime anime : entries) {
            validate(anime);
            if (loaded.putIfAbsent(anime.malId(), anime) != null)
                throw new IOException("The saved anime library contains duplicate IDs: " + anime.malId());
        }

        m_Anime.clear();
        m_Anime.putAll(loaded);
        m_Loaded = true;

        return List.copyOf(m_Anime.values());
    }

    public synchronized boolean add(Anime anime) throws IOException {
        requireLoaded();
        validate(anime);

        if (m_Anime.containsKey(anime.malId()))
            return false;

        LinkedHashMap<Integer, Anime> updated = new LinkedHashMap<>(m_Anime);
        updated.put(anime.malId(), anime);

        persist(updated);
        return true;
    }

    public synchronized boolean remove(int animeId) throws IOException {
        requireLoaded();

        if (!m_Anime.containsKey(animeId))
            return false;

        LinkedHashMap<Integer, Anime> updated = new LinkedHashMap<>(m_Anime);
        updated.remove(animeId);

        persist(updated);
        return true;
    }

    public synchronized boolean contains(int animeId) {
        return m_Anime.containsKey(animeId);
    }

    public synchronized List<Anime> getAll() {
        return List.copyOf(m_Anime.values());
    }

    private void persist(LinkedHashMap<Integer, Anime> updated) throws IOException {
        m_Repository.save(List.copyOf(updated.values()));
        m_Anime.clear();
        m_Anime.putAll(updated);
    }

    private void requireLoaded() {
        if (!m_Loaded)
            throw new IllegalStateException("The saved anime library has not loaded successfully.");
    }

    private void validate(Anime anime) throws IOException {
        if (anime == null || anime.malId() <= 0 || anime.title() == null || anime.title().isBlank())
            throw new IOException("The anime library contains an invalid anime entry.");
    }

}