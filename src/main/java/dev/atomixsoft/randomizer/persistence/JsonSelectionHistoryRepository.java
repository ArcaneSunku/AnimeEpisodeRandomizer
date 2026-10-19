package dev.atomixsoft.randomizer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atomixsoft.randomizer.model.SelectionRecord;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class JsonSelectionHistoryRepository implements SelectionHistoryRepository {

    private static final int SCHEMA_VERSION = 1;

    private record StoredSelection(String id, String sessionId, int animeId, String animeTitle, int episodeNumber, String episodeTitle, String selectedAt) {
    }

    private record HistoryDocument(int schemaVersion, List<StoredSelection> selections) {
    }

    private final Path m_File;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    public JsonSelectionHistoryRepository(Path file) {
        m_File = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    @Override
    public List<SelectionRecord> load() throws IOException {
        if (Files.notExists(m_File))
            return List.of();

        try {
            HistoryDocument document = m_Mapper.readValue(m_File.toFile(), HistoryDocument.class);
            if (document == null || document.schemaVersion() != SCHEMA_VERSION || document.selections() == null)
                throw new IOException("Unsupported or invalid selection history: " + m_File);

            List<SelectionRecord> records = createRecords(document);

            return List.copyOf(records);
        } catch (IOException exception) {
            throw new IOException("Unable to read selection history: " + m_File, exception);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid selection history: " + m_File, exception);
        }
    }

    private List<SelectionRecord> createRecords(HistoryDocument document) throws IOException {
        List<SelectionRecord> records = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        Set<Long> episodes = new HashSet<>();

        for (StoredSelection saved : document.selections()) {
            if (saved == null)
                throw new IOException("Null selection in history.");

            SelectionRecord record = new SelectionRecord(saved.id(), saved.sessionId(), saved.animeId(),
                    saved.animeTitle(), saved.episodeNumber(), saved.episodeTitle(), Instant.parse(saved.selectedAt()));

            if (!ids.add(record.id()) || !episodes.add(episodeKey(record.animeId(), record.episodeNumber())))
                throw new IOException("Duplicate selection in history: " + record.animeId() + "/" + record.episodeNumber());

            records.add(record);
        }
        return records;
    }

    @Override
    public void append(SelectionRecord selection) throws IOException {
        Objects.requireNonNull(selection, "Selection is required.");
        Files.createDirectories(m_File.getParent());

        Path lockFile = m_File.resolveSibling(m_File.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            List<SelectionRecord> records = new ArrayList<>(load());

            for (SelectionRecord previous : records) {
                if (previous.id().equals(selection.id()) ||
                        (previous.animeId() == selection.animeId() && previous.episodeNumber() == selection.episodeNumber()))
                    throw new IOException("This episode has already been selected.");
            }

            records.add(selection);
            save(records);
        }
    }

    private void save(List<SelectionRecord> records) throws IOException {
        List<StoredSelection> stored = records.stream()
                .map(record -> new StoredSelection(record.id(), record.sessionId(), record.animeId(),
                        record.animeTitle(), record.episodeNumber(), record.episodeTitle(), record.selectedAt().toString()))
                .toList();

        Path temporary = Files.createTempFile(m_File.getParent(), "selection-history-", ".tmp");
        try {
            Files.writeString(temporary, m_Mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HistoryDocument(SCHEMA_VERSION, stored)), StandardCharsets.UTF_8);

            try {
                Files.move(temporary, m_File, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, m_File, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private long episodeKey(int animeId, int episodeNumber) {
        return ((long) animeId << 32) | (episodeNumber & 0xffffffffL);
    }
}
