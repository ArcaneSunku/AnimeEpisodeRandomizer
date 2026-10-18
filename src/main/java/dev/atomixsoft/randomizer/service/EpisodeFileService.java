package dev.atomixsoft.randomizer.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class EpisodeFileService {

    private Path m_CurrentFile;
    private List<Integer> m_RemainingEpisodes = List.of();

    public int load(Path file) throws IOException {
        Path normalizedFile = file.toAbsolutePath().normalize();
        LinkedHashSet<Integer> episodes = new LinkedHashSet<>();

        for (String token : Files.readString(normalizedFile, StandardCharsets.UTF_8).split("\\s+")) {
            if (token.isBlank())
                continue;

            try {
                int episode = Integer.parseInt(token);

                if (episode <= 0)
                    throw new NumberFormatException("Episode must be positive");

                episodes.add(episode);
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid episode number: " + token, exception);
            }
        }

        m_CurrentFile = normalizedFile;
        m_RemainingEpisodes = new ArrayList<>(episodes);
        return m_RemainingEpisodes.size();
    }

    public int chooseRandomEpisode() throws IOException {
        if (m_CurrentFile == null || m_RemainingEpisodes.isEmpty())
            throw new IllegalStateException("No episodes available for selection.");

        int index = ThreadLocalRandom.current().nextInt(m_RemainingEpisodes.size());
        int episode = m_RemainingEpisodes.get(index);

        List<Integer> remaining = new ArrayList<>(m_RemainingEpisodes);
        remaining.remove(index);

        save(remaining);

        m_RemainingEpisodes = remaining;
        return episode;
    }

    private void save(List<Integer> episodes) throws IOException {
        Path temporaryFile = Files.createTempFile(
                m_CurrentFile.getParent(), "anime-episodes-", ".tmp"
        );

        try {
            String content = episodes.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(" "));

            Files.writeString(temporaryFile, content, StandardCharsets.UTF_8);

            try {
                Files.move(temporaryFile, m_CurrentFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, m_CurrentFile,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public int getRemainingCount() {
        return m_RemainingEpisodes.size();
    }

}