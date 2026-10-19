package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.cache.AnimeArtworkCache;
import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.SelectionRecord;
import dev.atomixsoft.randomizer.persistence.AnimeLibraryRepository;
import dev.atomixsoft.randomizer.service.EpisodeRandomizerService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class RandomizerPanel extends JPanel {

    private final AnimeLibraryRepository m_Library;
    private final EpisodeRandomizerService m_Randomizer;
    private final AnimeArtworkCache m_Artwork;
    private final Consumer<SelectionRecord> m_OnSelection;
    private final String m_SessionId = UUID.randomUUID().toString();

    private final DefaultListModel<Anime> m_AnimeModel = new DefaultListModel<>();
    private final JList<Anime> m_AnimeList = new JList<>(m_AnimeModel);

    private final JRadioButton m_SingleMode = new JRadioButton("One anime", true);
    private final JRadioButton m_MultipleMode = new JRadioButton("Selected group");

    private final JComboBox<String> m_Weighting = new JComboBox<>(new String[]{"Equal chance per episode", "Equal chance per anime"});
    private final JCheckBox m_ExcludeFiller = new JCheckBox("Exclude filler");
    private final JCheckBox m_ExcludeRecap = new JCheckBox("Exclude recaps");

    private final JButton m_ReloadButton = UITheme.button("Reload library", false);
    private final JButton m_RandomizeButton = UITheme.button("Randomize episode", true);

    private final JLabel m_AvailableCount = UITheme.label("—", 29, true, UITheme.TEXT);
    private final JLabel m_SessionCount = UITheme.label("0", 29, true, UITheme.TEXT);
    private final JLabel m_ResultAnime = UITheme.label("Choose an anime to begin", 17, true, UITheme.TEXT);
    private final JLabel m_ResultEpisode = UITheme.label("—", 33, true, UITheme.TEXT);
    private final JLabel m_ResultTitle = UITheme.label("Your next episode will appear here.", 13, false, UITheme.MUTED);
    private final JLabel m_Cover = new JLabel("NO COVER", SwingConstants.CENTER);
    private final JLabel m_Feedback = UITheme.label("Selections are saved and won't repeat.", 12, false, UITheme.MUTED);

    private int m_ResultAnimeId = -1;
    private boolean m_Busy;
    private long m_PreviewRevision;
    private int m_PicksThisRun;

    public RandomizerPanel(AnimeLibraryRepository library, EpisodeRandomizerService randomizer,
                           AnimeArtworkCache artwork, Consumer<SelectionRecord> onSelection) {
        super(new BorderLayout());
        m_Library = library;
        m_Randomizer = randomizer;
        m_Artwork = artwork;
        m_OnSelection = onSelection;
        setBackground(UITheme.BACKGROUND);

        JPanel content = UITheme.panel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(22, 32, 28, 32));
        addSection(content, createHeader());
        addSection(content, createConfiguration());
        addSection(content, createStatistics());
        addSection(content, createResultCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(UITheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);

        ButtonGroup modes = new ButtonGroup();
        modes.add(m_SingleMode);
        modes.add(m_MultipleMode);
        m_SingleMode.addActionListener(event -> updateSelectionMode());
        m_MultipleMode.addActionListener(event -> updateSelectionMode());

        m_AnimeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting())
                updateAvailability();
        });

        m_Weighting.addActionListener(event -> updateAvailability());
        m_ExcludeFiller.addActionListener(event -> updateAvailability());
        m_ExcludeRecap.addActionListener(event -> updateAvailability());
        m_ReloadButton.addActionListener(event -> reloadLibrary());
        m_RandomizeButton.addActionListener(event -> randomize());

        updateControls();
        reloadLibrary();
    }

    public void reloadLibrary() {
        if (m_Busy)
            return;

        Set<Integer> selected = new HashSet<>();
        for (Anime anime : m_AnimeList.getSelectedValuesList())
            selected.add(anime.malId());

        setBusy(true, "Loading saved anime...");
        new SwingWorker<List<Anime>, Void>() {
            @Override
            protected List<Anime> doInBackground() throws IOException {
                return m_Library.load();
            }

            @Override
            protected void done() {
                try {
                    List<Anime> anime = get();
                    m_AnimeModel.clear();
                    anime.forEach(m_AnimeModel::addElement);

                    List<Integer> indices = new ArrayList<>();
                    for (int i = 0; i < anime.size(); i++) {
                        if (selected.contains(anime.get(i).malId()))
                            indices.add(i);
                    }

                    if (m_SingleMode.isSelected() && !indices.isEmpty())
                        m_AnimeList.setSelectedIndex(indices.get(0));
                    else if (!indices.isEmpty())
                        m_AnimeList.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
                    else if (!anime.isEmpty())
                        m_AnimeList.setSelectedIndex(0);

                    m_Feedback.setText(anime.isEmpty()
                            ? "Your library is empty. Add anime in Anime Library first."
                            : "Choose an anime, then randomize an episode.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Loading interrupted", exception);
                } catch (ExecutionException exception) {
                    showError("Cannot load your library", exception.getCause());
                } finally {
                    setBusy(false, null);
                    updateAvailability();
                }
            }
        }.execute();
    }

    private JPanel createHeader() {
        JPanel header = UITheme.panel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel title = UITheme.label("Find your next episode", 29, true, UITheme.TEXT);
        JLabel subtitle = UITheme.label("Pick an anime from your saved library. Previously selected episodes stay excluded.",
                13, false, UITheme.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);
        return header;
    }

    private JPanel createConfiguration() {
        JPanel configuration = UITheme.panel(new GridLayout(1, 2, 14, 0));
        configuration.add(createSelectionSource());
        configuration.add(createOptions());
        return configuration;
    }

    private JPanel createSelectionSource() {
        JPanel card = UITheme.card(new BorderLayout(0, 12));
        JPanel heading = UITheme.panel(new BorderLayout(10, 0));
        heading.add(UITheme.label("SELECT ANIME", 12, true, UITheme.MUTED), BorderLayout.WEST);
        heading.add(m_ReloadButton, BorderLayout.EAST);
        card.add(heading, BorderLayout.NORTH);

        m_AnimeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_AnimeList.setBackground(UITheme.CARD);
        m_AnimeList.setForeground(UITheme.TEXT);
        m_AnimeList.setFixedCellHeight(34);
        m_AnimeList.setVisibleRowCount(4);
        m_AnimeList.setCellRenderer((list, anime, index, selected, focus) -> {
            JLabel label = UITheme.label(anime.title(), 13, false, UITheme.TEXT);
            label.setOpaque(true);
            label.setBackground(selected ? new Color(0x373157) : UITheme.CARD);
            label.setBorder(new EmptyBorder(6, 10, 6, 10));
            return label;
        });

        JScrollPane listScroll = new JScrollPane(m_AnimeList);
        listScroll.setPreferredSize(new Dimension(200, 104));
        card.add(listScroll, BorderLayout.CENTER);

        JPanel modes = UITheme.panel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        modes.add(m_SingleMode);
        modes.add(m_MultipleMode);
        card.add(modes, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createOptions() {
        JPanel card = UITheme.card(new BorderLayout(0, 10));
        card.add(UITheme.label("SELECTION OPTIONS", 12, true, UITheme.MUTED), BorderLayout.NORTH);

        JPanel options = UITheme.panel();
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));

        m_ExcludeFiller.setAlignmentX(LEFT_ALIGNMENT);
        m_ExcludeRecap.setAlignmentX(LEFT_ALIGNMENT);

        m_Weighting.setAlignmentX(LEFT_ALIGNMENT);
        m_Weighting.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        options.add(m_ExcludeFiller);
        options.add(Box.createVerticalStrut(7));
        options.add(m_ExcludeRecap);
        options.add(Box.createVerticalStrut(13));
        options.add(UITheme.label("Group weighting", 11, false, UITheme.MUTED));
        options.add(Box.createVerticalStrut(5));
        options.add(m_Weighting);

        card.add(options, BorderLayout.CENTER);

        m_Weighting.setEnabled(false);
        m_Weighting.setToolTipText("For group selection, choose whether every episode or every anime has equal odds.");
        return card;
    }

    private JPanel createStatistics() {
        JPanel stats = UITheme.panel(new GridLayout(1, 2, 16, 0));
        stats.add(statistic("AVAILABLE EPISODES", m_AvailableCount));
        stats.add(statistic("PICKS THIS SESSION", m_SessionCount));
        return stats;
    }

    private JPanel statistic(String title, JLabel value) {
        JPanel card = UITheme.card(new BorderLayout(0, 8));
        card.add(UITheme.label(title, 11, true, UITheme.MUTED), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel createResultCard() {
        JPanel card = UITheme.card(new BorderLayout(18, 12));
        card.add(UITheme.label("YOUR RANDOM EPISODE", 12, true, UITheme.MUTED), BorderLayout.NORTH);

        JPanel center = UITheme.panel(new BorderLayout(18, 0));
        m_Cover.setPreferredSize(new Dimension(116, 166));
        m_Cover.setOpaque(true);
        m_Cover.setBackground(UITheme.CARD_BORDER);
        m_Cover.setForeground(UITheme.MUTED);
        center.add(m_Cover, BorderLayout.WEST);

        JPanel info = UITheme.panel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        for (JLabel label : new JLabel[]{m_ResultAnime, m_ResultEpisode, m_ResultTitle})
            label.setAlignmentX(LEFT_ALIGNMENT);
        info.add(Box.createVerticalGlue());
        info.add(m_ResultAnime);
        info.add(Box.createVerticalStrut(12));
        info.add(m_ResultEpisode);
        info.add(Box.createVerticalStrut(8));
        info.add(m_ResultTitle);
        info.add(Box.createVerticalGlue());
        center.add(info, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        JPanel actions = UITheme.panel(new BorderLayout(0, 12));
        actions.add(m_RandomizeButton, BorderLayout.NORTH);
        actions.add(m_Feedback, BorderLayout.SOUTH);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void addSection(JPanel parent, JPanel section) {
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        parent.add(section);
        parent.add(Box.createVerticalStrut(12));
    }

    private void updateSelectionMode() {
        List<Anime> selected = m_AnimeList.getSelectedValuesList();

        m_AnimeList.setSelectionMode(m_MultipleMode.isSelected()
                ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);

        m_AnimeList.setToolTipText(m_MultipleMode.isSelected()
                ? "Ctrl-click (or Command-click) to select several anime." : "Choose one anime.");

        if (!selected.isEmpty()) {
            if (m_SingleMode.isSelected())
                m_AnimeList.setSelectedValue(selected.get(0), true);
            else {
                List<Integer> indices = new ArrayList<>();
                for (Anime anime : selected) {
                    for (int i = 0; i < m_AnimeModel.size(); i++) {
                        if (m_AnimeModel.get(i).malId() == anime.malId())
                            indices.add(i);
                    }
                }
                m_AnimeList.setSelectedIndices(indices.stream().mapToInt(Integer::intValue).toArray());
            }
        }

        updateControls();
        updateAvailability();
    }

    private EpisodeRandomizerService.Request request() {
        List<Integer> animeIds = m_AnimeList.getSelectedValuesList().stream().map(Anime::malId).toList();

        EpisodeRandomizerService.Weighting weighting = m_Weighting.getSelectedIndex() == 1
                ? EpisodeRandomizerService.Weighting.PER_ANIME : EpisodeRandomizerService.Weighting.PER_EPISODE;

        return new EpisodeRandomizerService.Request(animeIds, m_ExcludeFiller.isSelected(),
                m_ExcludeRecap.isSelected(), weighting, m_SessionId);
    }

    private void updateAvailability() {
        long revision = ++m_PreviewRevision;
        if (m_Busy || m_AnimeList.getSelectedValuesList().isEmpty()) {
            m_AvailableCount.setText("—");
            updateControls();
            return;
        }

        EpisodeRandomizerService.Request request = request();
        m_AvailableCount.setText("...");
        new SwingWorker<EpisodeRandomizerService.Availability, Void>() {
            @Override
            protected EpisodeRandomizerService.Availability doInBackground() throws IOException {
                return m_Randomizer.availability(request);
            }

            @Override
            protected void done() {
                if (revision != m_PreviewRevision || m_Busy)
                    return;
                try {
                    var availability = get();
                    m_AvailableCount.setText(Integer.toString(availability.eligibleEpisodes()));
                    m_Feedback.setText(availability.eligibleEpisodes() == 0
                            ? "No unselected episodes match these options."
                            : "Ready to choose from " + availability.eligibleEpisodes() + " unselected episodes.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException exception) {
                    m_AvailableCount.setText("—");
                    m_Feedback.setText(message(exception.getCause()));
                }
            }
        }.execute();
        updateControls();
    }

    private void randomize() {
        if (m_Busy || m_AnimeList.getSelectedValuesList().isEmpty())
            return;

        EpisodeRandomizerService.Request request = request();
        setBusy(true, "Saving your selection...");
        new SwingWorker<SelectionRecord, Void>() {
            @Override
            protected SelectionRecord doInBackground() throws IOException {
                return m_Randomizer.select(request);
            }

            @Override
            protected void done() {
                try {
                    SelectionRecord selection = get();

                    m_ResultAnimeId = selection.animeId();
                    m_ResultAnime.setText(selection.animeTitle());
                    m_ResultEpisode.setText("Episode " + selection.episodeNumber());
                    m_ResultTitle.setText(selection.episodeTitle());
                    m_ResultTitle.setToolTipText(selection.episodeTitle());
                    m_SessionCount.setText(Integer.toString(++m_PicksThisRun));
                    m_Feedback.setText("Selection saved. This episode won't be picked again.");
                    m_OnSelection.accept(selection);

                    updateResultArtwork(selection.animeId());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Selection interrupted", exception);
                } catch (ExecutionException exception) {
                    showError("Unable to choose an episode", exception.getCause());
                } finally {
                    setBusy(false, null);
                    updateAvailability();
                }
            }
        }.execute();
    }

    private void updateResultArtwork(int animeId) {
        for (int i = 0; i < m_AnimeModel.size(); i++) {
            Anime anime = m_AnimeModel.get(i);
            if (anime.malId() != animeId)
                continue;

            m_Cover.setIcon(null);
            m_Cover.setText("NO COVER");
            m_Artwork.load(anime).thenAccept(icon -> SwingUtilities.invokeLater(() -> {
                if (icon != null && m_ResultAnimeId == animeId) {
                    m_Cover.setIcon(icon);
                    m_Cover.setText("");
                }
            }));
            break;
        }
    }

    private void setBusy(boolean busy, String feedback) {
        m_Busy = busy;
        ++m_PreviewRevision;

        if (feedback != null)
            m_Feedback.setText(feedback);

        updateControls();
    }

    private void updateControls() {
        m_ReloadButton.setEnabled(!m_Busy);
        m_RandomizeButton.setEnabled(!m_Busy && !m_AnimeList.isSelectionEmpty());
        m_AnimeList.setEnabled(!m_Busy);
        m_SingleMode.setEnabled(!m_Busy);
        m_MultipleMode.setEnabled(!m_Busy);
        m_Weighting.setEnabled(!m_Busy && m_MultipleMode.isSelected());
        m_ExcludeFiller.setEnabled(!m_Busy);
        m_ExcludeRecap.setEnabled(!m_Busy);
    }

    private void showError(String title, Throwable cause) {
        String message = message(cause);
        m_Feedback.setText(message);
        m_Feedback.setToolTipText(message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private String message(Throwable cause) {
        return cause == null || cause.getMessage() == null ? "Unexpected error." : cause.getMessage();
    }

}
