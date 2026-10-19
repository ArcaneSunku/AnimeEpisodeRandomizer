package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.model.HistorySnapshot;
import dev.atomixsoft.randomizer.model.SelectionHistoryEntry;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.model.SessionRecord;
import dev.atomixsoft.randomizer.service.SelectionHistoryService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public final class HistoryPanel extends JPanel {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a")
            .withZone(ZoneId.systemDefault());

    private record SessionChoice(SessionRecord session, int selections) {
        @Override
        public String toString() {
            return session == null ? "All sessions" : session.name();
        }
    }

    private final SelectionHistoryService m_History;
    private final Runnable m_OnChanged;
    private final DefaultListModel<SessionChoice> m_SessionModel = new DefaultListModel<>();
    private final JList<SessionChoice> m_Sessions = new JList<>(m_SessionModel);
    private final DefaultListModel<SelectionHistoryEntry> m_EntryModel = new DefaultListModel<>();
    private final JList<SelectionHistoryEntry> m_Entries = new JList<>(m_EntryModel);
    private final JButton m_NewSession = UITheme.button("New session", true);
    private final JButton m_UseSession = UITheme.button("Use session", false);
    private final JButton m_RenameSession = UITheme.button("Rename", false);
    private final JButton m_Undo = UITheme.button("Undo selection", false);
    private final JButton m_ResetAnime = UITheme.button("Reset anime", false);
    private final JButton m_ResetAll = UITheme.button("Reset all", false);
    private final JButton m_Restore = UITheme.button("Restore backup", false);
    private final JLabel m_CurrentSession = UITheme.label("Current session: loading...", 12, false, UITheme.MUTED);
    private final JLabel m_Summary = UITheme.label("Loading selection history...", 12, false, UITheme.MUTED);
    private final JLabel m_Status = UITheme.label("Selections remain in your history after being undone or reset.",
            12, false, UITheme.MUTED);

    private HistorySnapshot m_Snapshot = new HistorySnapshot(List.of(), List.of());
    private boolean m_Busy;
    private long m_RefreshRevision;

    public HistoryPanel(SelectionHistoryService history, Runnable onChanged) {
        super(new BorderLayout());
        m_History = history;
        m_OnChanged = onChanged;
        setBackground(UITheme.BACKGROUND);

        JPanel content = UITheme.panel(new BorderLayout(0, 14));
        content.setBorder(new EmptyBorder(24, 28, 24, 28));
        content.add(createHeader(), BorderLayout.NORTH);
        content.add(createWorkspace(), BorderLayout.CENTER);
        content.add(m_Status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        configureLists();
        m_NewSession.addActionListener(event -> newSession());
        m_UseSession.addActionListener(event -> useSession());
        m_RenameSession.addActionListener(event -> renameSession());
        m_Undo.addActionListener(event -> undo());
        m_ResetAnime.addActionListener(event -> resetAnime());
        m_ResetAll.addActionListener(event -> resetAll());
        m_Restore.addActionListener(event -> restoreBackup());
        m_Sessions.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                rebuildEntries(null);
                updateControls();
            }
        });
        m_Entries.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting())
                updateControls();
        });
        updateControls();
        refresh();
    }

    public void refresh() {
        long revision = ++m_RefreshRevision;
        new SwingWorker<HistorySnapshot, Void>() {
            @Override
            protected HistorySnapshot doInBackground() throws IOException {
                return m_History.snapshot();
            }

            @Override
            protected void done() {
                if (revision != m_RefreshRevision)
                    return;
                try {
                    showSnapshot(get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    reportError(exception.getCause());
                }
            }
        }.execute();
    }

    private JPanel createHeader() {
        JPanel header = UITheme.panel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = UITheme.label("History & sessions", 27, true, UITheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        m_CurrentSession.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(7));
        header.add(m_CurrentSession);
        return header;
    }

    private JPanel createWorkspace() {
        JPanel workspace = UITheme.panel(new BorderLayout(14, 0));
        JPanel sessions = UITheme.card(new BorderLayout(0, 12));
        sessions.setPreferredSize(new Dimension(214, 0));
        sessions.add(UITheme.label("SESSIONS", 12, true, UITheme.MUTED), BorderLayout.NORTH);
        sessions.add(new JScrollPane(m_Sessions), BorderLayout.CENTER);

        JPanel sessionActions = UITheme.panel(new GridLayout(3, 1, 0, 7));
        sessionActions.add(m_NewSession);
        sessionActions.add(m_UseSession);
        sessionActions.add(m_RenameSession);
        sessions.add(sessionActions, BorderLayout.SOUTH);
        workspace.add(sessions, BorderLayout.WEST);

        JPanel selections = UITheme.card(new BorderLayout(0, 12));
        JPanel selectionHeader = UITheme.panel(new BorderLayout());
        selectionHeader.add(UITheme.label("SELECTION HISTORY", 12, true, UITheme.MUTED), BorderLayout.NORTH);
        selectionHeader.add(m_Summary, BorderLayout.SOUTH);
        selections.add(selectionHeader, BorderLayout.NORTH);
        selections.add(new JScrollPane(m_Entries), BorderLayout.CENTER);

        JPanel actions = UITheme.panel(new GridLayout(4, 1, 0, 7));
        actions.add(m_Undo);
        actions.add(m_ResetAnime);
        actions.add(m_ResetAll);
        actions.add(m_Restore);
        selections.add(actions, BorderLayout.SOUTH);
        workspace.add(selections, BorderLayout.CENTER);
        return workspace;
    }

    private void configureLists() {
        m_Sessions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_Sessions.setBackground(UITheme.CARD);
        m_Sessions.setFixedCellHeight(66);
        m_Sessions.setCellRenderer((list, choice, index, selected, focus) -> {
            JPanel row = UITheme.panel(new BorderLayout(0, 4));
            row.setOpaque(true);
            row.setBackground(selected ? new Color(0x373157) : UITheme.CARD);
            row.setBorder(new EmptyBorder(9, 10, 9, 10));
            String name = choice.session() == null ? "All sessions" : choice.session().name();
            JLabel title = UITheme.label(name, 13, true, UITheme.TEXT);
            row.add(title, BorderLayout.NORTH);
            String info = choice.session() == null ? "Every recorded selection"
                    : choice.selections() + " active · " + TIME_FORMAT.format(choice.session().startedAt());
            JLabel subtitle = UITheme.label(info, 10, false, UITheme.MUTED);
            row.add(subtitle, BorderLayout.SOUTH);
            row.setToolTipText(name);
            return row;
        });

        m_Entries.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_Entries.setBackground(UITheme.CARD);
        m_Entries.setFixedCellHeight(76);
        m_Entries.setCellRenderer((list, entry, index, selected, focus) -> {
            SelectionRecord selection = entry.selection();
            JPanel row = UITheme.panel(new BorderLayout(0, 5));
            row.setOpaque(true);
            row.setBackground(selected ? new Color(0x373157) : UITheme.CARD);
            row.setBorder(new EmptyBorder(9, 10, 9, 10));
            JLabel title = UITheme.label(selection.animeTitle() + " — Episode " + selection.episodeNumber(),
                    13, true, UITheme.TEXT);
            JLabel detail = UITheme.label(selection.episodeTitle(), 11, false, UITheme.MUTED);
            JLabel date = UITheme.label(TIME_FORMAT.format(selection.selectedAt()) + " · " + statusLabel(entry),
                    10, false, entry.active() ? UITheme.ACCENT : UITheme.MUTED);
            row.add(title, BorderLayout.NORTH);
            JPanel lower = UITheme.panel(new GridLayout(2, 1, 0, 2));
            lower.add(detail);
            lower.add(date);
            row.add(lower, BorderLayout.CENTER);
            row.setToolTipText(selection.animeTitle() + " — " + selection.episodeTitle());
            return row;
        });
    }

    private String statusLabel(SelectionHistoryEntry entry) {
        return switch (entry.status()) {
            case ACTIVE -> "Selected";
            case UNDONE -> "Undone";
            case RESET -> "Reset";
        };
    }

    private void showSnapshot(HistorySnapshot snapshot) {
        String sessionId = selectedSessionId();
        String selectedEntryId = selectedEntryId();
        m_Snapshot = snapshot;
        m_SessionModel.clear();
        m_SessionModel.addElement(new SessionChoice(null, snapshot.activeSelections().size()));

        List<SessionRecord> sessions = new ArrayList<>(snapshot.sessions());
        sessions.sort(Comparator.comparing(SessionRecord::startedAt).reversed());
        for (SessionRecord session : sessions) {
            int active = (int) snapshot.entries().stream().filter(SelectionHistoryEntry::active)
                    .filter(entry -> entry.selection().sessionId().equals(session.id())).count();
            m_SessionModel.addElement(new SessionChoice(session, active));
        }

        int select = 0;
        if (sessionId != null) {
            for (int i = 1; i < m_SessionModel.size(); i++) {
                if (m_SessionModel.get(i).session().id().equals(sessionId)) {
                    select = i;
                    break;
                }
            }
        }
        m_Sessions.setSelectedIndex(select);
        rebuildEntries(selectedEntryId);
        m_CurrentSession.setText("Current session: " + m_History.currentSession()
                .map(SessionRecord::name).orElse("Not started"));
        updateControls();
    }

    private void rebuildEntries(String preserveId) {
        m_EntryModel.clear();
        String sessionId = selectedSessionId();
        m_Snapshot.entries().stream()
                .filter(entry -> sessionId == null || entry.selection().sessionId().equals(sessionId))
                .sorted(Comparator.comparing((SelectionHistoryEntry entry) -> entry.selection().selectedAt()).reversed())
                .forEach(m_EntryModel::addElement);
        if (preserveId != null) {
            for (int i = 0; i < m_EntryModel.size(); i++) {
                if (m_EntryModel.get(i).selection().id().equals(preserveId)) {
                    m_Entries.setSelectedIndex(i);
                    break;
                }
            }
        }
        long active = java.util.stream.IntStream.range(0, m_EntryModel.size())
                .filter(index -> m_EntryModel.get(index).active()).count();
        m_Summary.setText(active + " active · " + m_EntryModel.size() + " recorded");
        updateControls();
    }

    private String selectedSessionId() {
        SessionChoice selected = m_Sessions.getSelectedValue();
        return selected == null || selected.session() == null ? null : selected.session().id();
    }

    private String selectedEntryId() {
        SelectionHistoryEntry selected = m_Entries.getSelectedValue();
        return selected == null ? null : selected.selection().id();
    }

    private void newSession() {
        String name = JOptionPane.showInputDialog(this, "Session name (optional):", "New session",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null)
            return;
        execute("Creating session...", () -> m_History.startSession(name), "Session created.");
    }

    private void useSession() {
        String sessionId = selectedSessionId();
        if (sessionId != null)
            execute("Switching session...", () -> m_History.useSession(sessionId), "Session activated.");
    }

    private void renameSession() {
        SessionChoice choice = m_Sessions.getSelectedValue();
        if (choice == null || choice.session() == null)
            return;
        String name = JOptionPane.showInputDialog(this, "Session name:", choice.session().name());
        if (name == null)
            return;
        execute("Renaming session...", () -> {
            m_History.renameSession(choice.session().id(), name);
            return null;
        }, "Session renamed.");
    }

    private void undo() {
        SelectionHistoryEntry entry = m_Entries.getSelectedValue();
        if (entry == null || !entry.active())
            return;
        execute("Restoring episode eligibility...", () -> {
            m_History.undo(entry.selection().id());
            return null;
        }, "Selection undone. The episode is eligible again.");
    }

    private void resetAnime() {
        SelectionHistoryEntry entry = m_Entries.getSelectedValue();
        if (entry == null)
            return;
        int animeId = entry.selection().animeId();
        String name = entry.selection().animeTitle();
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Restore eligibility for all selected episodes of " + name + "?\n"
                        + "The selections remain visible in history.",
                "Reset anime selections", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation == JOptionPane.YES_OPTION)
            execute("Resetting anime selections...", () -> m_History.resetAnime(animeId),
                    "Episode eligibility restored for " + name + ".");
    }

    private void resetAll() {
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Restore eligibility for every previously selected episode?\n"
                        + "This affects all anime and sessions. History records will remain available.",
                "Reset all selections", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation == JOptionPane.YES_OPTION)
            execute("Resetting all selections...", m_History::resetAll,
                    "All previously selected episodes are eligible again.");
    }

    private void restoreBackup() {
        int confirmation = JOptionPane.showConfirmDialog(this,
                "Restore the previous valid history file?\n"
                        + "This replaces the current history with its backup, including any newer changes.",
                "Restore history backup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmation == JOptionPane.YES_OPTION)
            execute("Restoring history backup...", () -> {
                m_History.restoreBackup();
                return null;
            }, "History backup restored.");
    }

    private <T> void execute(String progress, Callable<T> operation, String success) {
        if (m_Busy)
            return;
        m_Busy = true;
        m_Status.setText(progress);
        updateControls();
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                try {
                    get();
                    m_Status.setText(success);
                    m_OnChanged.run();
                    refresh();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    reportError(exception.getCause());
                } finally {
                    m_Busy = false;
                    updateControls();
                }
            }
        }.execute();
    }

    private void updateControls() {
        SessionChoice session = m_Sessions.getSelectedValue();
        SelectionHistoryEntry entry = m_Entries.getSelectedValue();
        boolean sessionSelected = session != null && session.session() != null;
        m_NewSession.setEnabled(!m_Busy);
        m_UseSession.setEnabled(!m_Busy && sessionSelected && m_History.currentSession()
                .map(current -> !current.id().equals(session.session().id())).orElse(true));
        m_RenameSession.setEnabled(!m_Busy && sessionSelected);
        m_Undo.setEnabled(!m_Busy && entry != null && entry.active());
        m_ResetAnime.setEnabled(!m_Busy && entry != null && m_Snapshot.entries().stream()
                .anyMatch(value -> value.active() && value.selection().animeId() == entry.selection().animeId()));
        m_ResetAll.setEnabled(!m_Busy && !m_Snapshot.activeSelections().isEmpty());
        m_Restore.setEnabled(!m_Busy && m_History.hasBackup());
        m_Sessions.setEnabled(!m_Busy);
        m_Entries.setEnabled(!m_Busy);
    }

    private void reportError(Throwable error) {
        String message = error == null || error.getMessage() == null ? "Unexpected history error." : error.getMessage();
        m_Status.setText(message);
        m_Status.setToolTipText(message);
        JOptionPane.showMessageDialog(this, message, "History operation failed", JOptionPane.ERROR_MESSAGE);
    }
}
