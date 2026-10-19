package dev.atomixsoft.randomizer.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atomixsoft.randomizer.model.HistorySnapshot;
import dev.atomixsoft.randomizer.model.SelectionHistoryEntry;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.model.SessionRecord;

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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonSelectionHistoryRepository implements SelectionHistoryRepository {
    private static final int SCHEMA_VERSION = 2;
    private static final DateTimeFormatter SESSION_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy")
            .withZone(ZoneOffset.UTC);

    private record StoredSelection(String id, String sessionId, int animeId, String animeTitle,
                                   int episodeNumber, String episodeTitle, String selectedAt) {
    }

    private record StoredEntry(StoredSelection selection, String status, String changedAt) {
    }

    private record StoredSession(String id, String name, String startedAt) {
    }

    private record OldDocument(int schemaVersion, List<StoredSelection> selections) {
    }

    private record HistoryDocument(int schemaVersion, List<StoredSession> sessions, List<StoredEntry> selections) {
    }

    @FunctionalInterface
    private interface Mutation<T> {
        T apply(List<SessionRecord> sessions, List<SelectionHistoryEntry> entries) throws IOException;
    }

    private static final Map<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private final Path m_File;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    public JsonSelectionHistoryRepository(Path file) {
        m_File = Objects.requireNonNull(file).toAbsolutePath().normalize();
    }

    @Override
    public List<SelectionRecord> load() throws IOException {
        return snapshot().activeSelections();
    }

    @Override
    public HistorySnapshot snapshot() throws IOException {
        return readFile(m_File);
    }

    private HistorySnapshot readFile(Path path) throws IOException {
        if (Files.notExists(path))
            return new HistorySnapshot(List.of(), List.of());

        try {
            JsonNode root = m_Mapper.readTree(path.toFile());
            if (root == null || !root.path("schemaVersion").isIntegralNumber())
                throw new IOException("Missing history schema version.");

            int schema = root.path("schemaVersion").intValue();
            if (schema == 1)
                return readLegacy(path);
            if (schema != SCHEMA_VERSION)
                throw new IOException("Unsupported selection history version: " + schema);

            HistoryDocument document = m_Mapper.treeToValue(root, HistoryDocument.class);
            if (document.sessions() == null || document.selections() == null)
                throw new IOException("Missing sessions or selections in history.");

            List<SessionRecord> sessions = new ArrayList<>();
            for (StoredSession value : document.sessions()) {
                if (value == null)
                    throw new IOException("Null session in history.");
                sessions.add(new SessionRecord(value.id(), value.name(), Instant.parse(value.startedAt())));
            }

            List<SelectionHistoryEntry> entries = new ArrayList<>();
            for (StoredEntry value : document.selections()) {
                if (value == null || value.selection() == null)
                    throw new IOException("Null selection in history.");
                SelectionHistoryEntry.Status status = SelectionHistoryEntry.Status.valueOf(value.status());
                Instant changedAt = value.changedAt() == null ? null : Instant.parse(value.changedAt());
                entries.add(new SelectionHistoryEntry(fromStored(value.selection()), status, changedAt));
            }

            HistorySnapshot result = new HistorySnapshot(sessions, entries);
            validate(result);
            return result;
        } catch (IOException exception) {
            throw new IOException("Unable to read selection history: " + m_File, exception);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid selection history: " + m_File, exception);
        }
    }

    private HistorySnapshot readLegacy(Path path) throws IOException {
        OldDocument document = m_Mapper.readValue(path.toFile(), OldDocument.class);
        if (document.selections() == null)
            throw new IOException("Missing selections in legacy history.");

        Map<String, SessionRecord> sessions = new LinkedHashMap<>();
        List<SelectionHistoryEntry> entries = new ArrayList<>();
        for (StoredSelection stored : document.selections()) {
            if (stored == null)
                throw new IOException("Null selection in legacy history.");
            SelectionRecord selection = fromStored(stored);
            sessions.computeIfAbsent(selection.sessionId(), id -> new SessionRecord(id,
                    "Session " + SESSION_DATE.format(selection.selectedAt()), selection.selectedAt()));
            entries.add(new SelectionHistoryEntry(selection, SelectionHistoryEntry.Status.ACTIVE, null));
        }

        HistorySnapshot result = new HistorySnapshot(List.copyOf(sessions.values()), entries);
        validate(result);
        return result;
    }

    private SelectionRecord fromStored(StoredSelection value) {
        return new SelectionRecord(value.id(), value.sessionId(), value.animeId(), value.animeTitle(),
                value.episodeNumber(), value.episodeTitle(), Instant.parse(value.selectedAt()));
    }

    @Override
    public void append(SelectionRecord selection) throws IOException {
        Objects.requireNonNull(selection, "Selection is required.");
        mutate((sessions, entries) -> {
            boolean sessionExists = sessions.stream().anyMatch(session -> session.id().equals(selection.sessionId()));
            if (!sessionExists)
                sessions.add(new SessionRecord(selection.sessionId(),
                        "Session " + SESSION_DATE.format(selection.selectedAt()), selection.selectedAt()));

            for (SelectionHistoryEntry previous : entries) {
                SelectionRecord record = previous.selection();
                if (record.id().equals(selection.id()))
                    throw new IOException("Selection identifier already exists.");
                if (previous.active() && record.animeId() == selection.animeId()
                        && record.episodeNumber() == selection.episodeNumber())
                    throw new IOException("This episode has already been selected.");
            }
            entries.add(new SelectionHistoryEntry(selection, SelectionHistoryEntry.Status.ACTIVE, null));
            return null;
        });
    }

    @Override
    public void createSession(SessionRecord session) throws IOException {
        Objects.requireNonNull(session, "Session is required.");
        mutate((sessions, entries) -> {
            if (sessions.stream().anyMatch(previous -> previous.id().equals(session.id())))
                throw new IOException("Session identifier already exists.");
            sessions.add(session);
            return null;
        });
    }

    @Override
    public void renameSession(String sessionId, String name) throws IOException {
        mutate((sessions, entries) -> {
            for (int index = 0; index < sessions.size(); index++) {
                SessionRecord previous = sessions.get(index);
                if (previous.id().equals(sessionId)) {
                    sessions.set(index, new SessionRecord(previous.id(), name, previous.startedAt()));
                    return null;
                }
            }
            throw new IOException("Session not found.");
        });
    }

    @Override
    public void undo(String selectionId, Instant changedAt) throws IOException {
        Objects.requireNonNull(changedAt, "Change timestamp is required.");
        mutate((sessions, entries) -> {
            for (int index = 0; index < entries.size(); index++) {
                SelectionHistoryEntry previous = entries.get(index);
                if (previous.selection().id().equals(selectionId)) {
                    if (!previous.active())
                        throw new IOException("That selection has already been restored.");
                    entries.set(index, new SelectionHistoryEntry(previous.selection(),
                            SelectionHistoryEntry.Status.UNDONE, changedAt));
                    return null;
                }
            }
            throw new IOException("Selection not found.");
        });
    }

    @Override
    public int resetAnime(int animeId, Instant changedAt) throws IOException {
        if (animeId <= 0)
            throw new IllegalArgumentException("Anime ID must be positive.");
        return reset(entry -> entry.selection().animeId() == animeId, changedAt);
    }

    @Override
    public int resetAll(Instant changedAt) throws IOException {
        return reset(entry -> true, changedAt);
    }

    private int reset(java.util.function.Predicate<SelectionHistoryEntry> filter, Instant changedAt) throws IOException {
        Objects.requireNonNull(changedAt, "Change timestamp is required.");
        return mutate((sessions, entries) -> {
            int count = 0;
            for (int index = 0; index < entries.size(); index++) {
                SelectionHistoryEntry previous = entries.get(index);
                if (previous.active() && filter.test(previous)) {
                    entries.set(index, new SelectionHistoryEntry(previous.selection(),
                            SelectionHistoryEntry.Status.RESET, changedAt));
                    count++;
                }
            }
            return count;
        });
    }

    private <T> T mutate(Mutation<T> mutation) throws IOException {
        Files.createDirectories(m_File.getParent());
        synchronized (FILE_LOCKS.computeIfAbsent(m_File, ignored -> new Object())) {
            Path lockPath = m_File.resolveSibling(m_File.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                HistorySnapshot current = snapshot();
                List<SessionRecord> sessions = new ArrayList<>(current.sessions());
                List<SelectionHistoryEntry> entries = new ArrayList<>(current.entries());
                T result = mutation.apply(sessions, entries);
                HistorySnapshot updated = new HistorySnapshot(sessions, entries);
                validate(updated);
                write(updated);
                return result;
            }
        }
    }

    private void write(HistorySnapshot snapshot) throws IOException {
        List<StoredSession> sessions = snapshot.sessions().stream()
                .map(session -> new StoredSession(session.id(), session.name(), session.startedAt().toString())).toList();
        List<StoredEntry> entries = snapshot.entries().stream().map(entry -> {
            SelectionRecord record = entry.selection();
            StoredSelection saved = new StoredSelection(record.id(), record.sessionId(), record.animeId(),
                    record.animeTitle(), record.episodeNumber(), record.episodeTitle(), record.selectedAt().toString());
            return new StoredEntry(saved, entry.status().name(),
                    entry.changedAt() == null ? null : entry.changedAt().toString());
        }).toList();

        if (Files.isRegularFile(m_File)) {
            Path backup = backupFile();
            Path backupTemp = Files.createTempFile(m_File.getParent(), "history-backup-", ".tmp");
            try {
                Files.copy(m_File, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                moveFile(backupTemp, backup);
            } finally {
                Files.deleteIfExists(backupTemp);
            }
        }

        Path temporary = Files.createTempFile(m_File.getParent(), "selection-history-", ".tmp");
        try {
            Files.writeString(temporary, m_Mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new HistoryDocument(SCHEMA_VERSION, sessions, entries)), StandardCharsets.UTF_8);
            moveFile(temporary, m_File);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public boolean hasBackup() {
        return Files.isRegularFile(backupFile());
    }

    @Override
    public void restoreBackup() throws IOException {
        Path backup = backupFile();
        synchronized (FILE_LOCKS.computeIfAbsent(m_File, ignored -> new Object())) {
            Path lockPath = m_File.resolveSibling(m_File.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                if (Files.notExists(backup))
                    throw new IOException("No history backup is available.");
                readFile(backup);
                Path temporary = Files.createTempFile(m_File.getParent(), "history-restore-", ".tmp");
                try {
                    Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
                    moveFile(temporary, m_File);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        }
    }

    private Path backupFile() {
        return m_File.resolveSibling(m_File.getFileName() + ".bak");
    }

    private void moveFile(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void validate(HistorySnapshot snapshot) throws IOException {
        Set<String> sessionIds = new HashSet<>();
        for (SessionRecord session : snapshot.sessions()) {
            if (session == null || !sessionIds.add(session.id()))
                throw new IOException("Duplicate or invalid session identifier.");
        }

        Set<String> selectionIds = new HashSet<>();
        Set<Long> activeEpisodes = new HashSet<>();
        for (SelectionHistoryEntry entry : snapshot.entries()) {
            if (entry == null || !selectionIds.add(entry.selection().id()))
                throw new IOException("Duplicate or invalid selection identifier.");
            SelectionRecord selection = entry.selection();
            if (!sessionIds.contains(selection.sessionId()))
                throw new IOException("Selection references an unknown session.");
            if (entry.active() && !activeEpisodes.add(episodeKey(selection.animeId(), selection.episodeNumber())))
                throw new IOException("Duplicate active episode selection.");
        }
    }

    private long episodeKey(int animeId, int episodeNumber) {
        return ((long) animeId << 32) | (episodeNumber & 0xffffffffL);
    }
}
