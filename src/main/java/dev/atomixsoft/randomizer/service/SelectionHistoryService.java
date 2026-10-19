package dev.atomixsoft.randomizer.service;

import dev.atomixsoft.randomizer.model.HistorySnapshot;
import dev.atomixsoft.randomizer.model.SelectionHistoryEntry;
import dev.atomixsoft.randomizer.model.SessionRecord;
import dev.atomixsoft.randomizer.persistence.SelectionHistoryRepository;

import java.io.IOException;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SelectionHistoryService {
    private static final DateTimeFormatter SESSION_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    private final SelectionHistoryRepository m_Repository;
    private final Clock m_Clock;
    private SessionRecord m_CurrentSession;

    public SelectionHistoryService(SelectionHistoryRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public SelectionHistoryService(SelectionHistoryRepository repository, Clock clock) {
        m_Repository = Objects.requireNonNull(repository);
        m_Clock = Objects.requireNonNull(clock);
    }

    public synchronized SessionRecord ensureSession() throws IOException {
        if (m_CurrentSession == null)
            return startSession(null);
        return m_CurrentSession;
    }

    public synchronized SessionRecord startSession(String name) throws IOException {
        String sessionName = name == null || name.isBlank()
                ? "Session " + SESSION_TIME.format(m_Clock.instant()) : name.trim();
        SessionRecord session = new SessionRecord(UUID.randomUUID().toString(), sessionName, m_Clock.instant());
        m_Repository.createSession(session);
        m_CurrentSession = session;
        return session;
    }

    public synchronized SessionRecord useSession(String sessionId) throws IOException {
        SessionRecord session = m_Repository.snapshot().sessions().stream()
                .filter(value -> value.id().equals(sessionId)).findFirst()
                .orElseThrow(() -> new IOException("Session not found."));
        m_CurrentSession = session;
        return session;
    }

    public synchronized void renameSession(String sessionId, String name) throws IOException {
        m_Repository.renameSession(sessionId, name);
        if (m_CurrentSession != null && m_CurrentSession.id().equals(sessionId))
            m_CurrentSession = new SessionRecord(sessionId, name, m_CurrentSession.startedAt());
    }

    public synchronized Optional<SessionRecord> currentSession() {
        return Optional.ofNullable(m_CurrentSession);
    }

    public HistorySnapshot snapshot() throws IOException {
        return m_Repository.snapshot();
    }

    public void undo(String selectionId) throws IOException {
        m_Repository.undo(selectionId, m_Clock.instant());
    }

    public int resetAnime(int animeId) throws IOException {
        return m_Repository.resetAnime(animeId, m_Clock.instant());
    }

    public int resetAll() throws IOException {
        return m_Repository.resetAll(m_Clock.instant());
    }

    public boolean hasBackup() {
        return m_Repository.hasBackup();
    }

    public synchronized void restoreBackup() throws IOException {
        m_Repository.restoreBackup();
        HistorySnapshot restored = m_Repository.snapshot();
        String previousId = m_CurrentSession == null ? null : m_CurrentSession.id();
        m_CurrentSession = restored.sessions().stream()
                .filter(session -> session.id().equals(previousId)).findFirst()
                .orElseGet(() -> restored.sessions().stream()
                        .max(java.util.Comparator.comparing(SessionRecord::startedAt)).orElse(null));
        if (m_CurrentSession == null)
            ensureSession();
    }

    public int activeCount(String sessionId) throws IOException {
        return (int) snapshot().entries().stream()
                .filter(SelectionHistoryEntry::active)
                .filter(entry -> entry.selection().sessionId().equals(sessionId)).count();
    }
}
