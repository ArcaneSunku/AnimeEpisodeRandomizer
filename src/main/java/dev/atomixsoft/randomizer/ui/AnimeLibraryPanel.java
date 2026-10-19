package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.api.AnimeCatalogClient;
import dev.atomixsoft.randomizer.cache.AnimeArtworkCache;
import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.EpisodeCatalog;
import dev.atomixsoft.randomizer.model.Page;
import dev.atomixsoft.randomizer.service.AnimeLibraryService;
import dev.atomixsoft.randomizer.service.EpisodeCatalogService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class AnimeLibraryPanel extends JPanel {

    private static final int LOCAL_EPISODES_PER_PAGE = 25;
    private static final DateTimeFormatter CACHE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a").withZone(ZoneId.systemDefault());

    private interface CatalogUpdate {
    }

    private record CachedEpisodes(EpisodeCatalog catalog) implements CatalogUpdate {
    }

    private record RequestedEpisodePage(int number) implements CatalogUpdate {
    }

    private final AnimeCatalogClient m_Catalog;
    private final AnimeLibraryService m_Library;
    private final AnimeArtworkCache m_Artwork;
    private final EpisodeCatalogService m_EpisodeCatalogs;

    private final JTextField m_QueryField = new JTextField(24);
    private final JButton m_SearchButton = UITheme.button("Search", true);
    private final JButton m_PreviousResults = UITheme.button("Previous", false);
    private final JButton m_NextResults = UITheme.button("Next", false);
    private final JButton m_PreviousEpisodes = UITheme.button("Previous", false);
    private final JButton m_NextEpisodes = UITheme.button("Next", false);

    private final JLabel m_ResultPage = UITheme.label("Page —", 12, false, UITheme.MUTED);
    private final JLabel m_EpisodePage = UITheme.label("Page —", 12, false, UITheme.MUTED);
    private final JLabel m_EpisodeHeading = UITheme.label("EPISODES", 12, true, UITheme.MUTED);
    private final JLabel m_Status = UITheme.label("Search for an anime to browse its episodes.", 12, false, UITheme.MUTED);

    private final DefaultListModel<Anime> m_AnimeModel = new DefaultListModel<>();
    private final DefaultListModel<AnimeEpisode> m_EpisodeModel = new DefaultListModel<>();

    private final JList<Anime> m_AnimeList = new JList<>(m_AnimeModel);
    private final JList<AnimeEpisode> m_EpisodeList = new JList<>(m_EpisodeModel);

    private final JButton m_AddButton = UITheme.button("Add to library", true);
    private final JButton m_RemoveButton = UITheme.button("Remove from library", false);
    private final JButton m_ViewDiscoveredEpisodes = UITheme.button("View episodes", false);
    private final JButton m_ViewSavedEpisodes = UITheme.button("View episodes", false);

    private final DefaultListModel<Anime> m_SavedModel = new DefaultListModel<>();
    private final JList<Anime> m_SavedList = new JList<>(m_SavedModel);
    private final JTabbedPane m_Tabs = new JTabbedPane();

    private final CardLayout m_ViewLayout = new CardLayout();
    private final JPanel m_Views = new JPanel(m_ViewLayout);
    private final JButton m_BackButton = UITheme.button("Back to collection", false);
    private final JButton m_CacheEpisodesButton = UITheme.button("Refresh episode list", false);

    private String m_LastQuery;
    private Anime m_SelectedAnime;
    private EpisodeCatalog m_CurrentCatalog;

    private int m_CurrentResultPage;
    private int m_CurrentEpisodePage;

    private boolean m_UsingLocalCatalog;
    private boolean m_LoadingSavedEpisodes;
    private boolean m_EpisodeViewDismissed;

    private boolean m_LibraryReady;
    private boolean m_HasMoreResults;
    private boolean m_HasMoreEpisodes;
    private boolean m_Busy;

    public AnimeLibraryPanel(AnimeCatalogClient catalog, AnimeLibraryService library,
                             AnimeArtworkCache artwork, EpisodeCatalogService episodeCatalogs) {
        super(new BorderLayout());

        m_Catalog = catalog;
        m_Library = library;
        m_Artwork = artwork;
        m_EpisodeCatalogs = episodeCatalogs;

        setBackground(UITheme.BACKGROUND);

        JPanel content = UITheme.panel(new BorderLayout(0, 18));
        content.setBorder(new EmptyBorder(30, 36, 30, 36));
        content.add(createHeader(), BorderLayout.NORTH);

        m_Views.setOpaque(false);
        m_Views.add(createResultsPanel(), "collection");
        m_Views.add(createEpisodesPanel(), "episodes");

        content.add(m_Views, BorderLayout.CENTER);
        content.add(m_Status, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        m_QueryField.addActionListener(event -> search(1));
        m_SearchButton.addActionListener(event -> search(1));
        m_PreviousResults.addActionListener(event -> search(m_CurrentResultPage - 1));
        m_NextResults.addActionListener(event -> search(m_CurrentResultPage + 1));

        m_PreviousEpisodes.addActionListener(event -> changeEpisodePage(m_CurrentEpisodePage - 1));
        m_NextEpisodes.addActionListener(event -> changeEpisodePage(m_CurrentEpisodePage + 1));

        m_AnimeList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting())
                updateButtons();
        });

        m_SavedList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting())
                updateButtons();
        });

        m_CacheEpisodesButton.addActionListener(event -> cacheSelectedEpisodes());

        m_ViewDiscoveredEpisodes.addActionListener(event -> openSelectedAnime(m_AnimeList));
        m_ViewSavedEpisodes.addActionListener(event -> openSelectedAnime(m_SavedList));

        m_BackButton.addActionListener(event -> {
            m_EpisodeViewDismissed = true;
            m_ViewLayout.show(m_Views, "collection");
        });

        m_Tabs.addChangeListener(event -> updateButtons());
        m_AddButton.addActionListener(event -> addSelectedAnime());
        m_RemoveButton.addActionListener(event -> removeSelectedAnime());

        updateButtons();
        loadSavedLibrary();
    }

    private JPanel createHeader() {
        JPanel header = UITheme.panel(new BorderLayout(0, 14));
        JPanel labels = UITheme.panel();
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        JLabel title = UITheme.label("Anime library", 29, true, UITheme.TEXT);
        JLabel subtitle = UITheme.label("Search anime and explore episode lists.", 13, false, UITheme.MUTED);

        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        labels.add(title);
        labels.add(Box.createVerticalStrut(7));
        labels.add(subtitle);
        header.add(labels, BorderLayout.NORTH);

        JPanel searchBar = UITheme.panel(new BorderLayout(12, 0));
        m_QueryField.putClientProperty("JTextField.placeholderText", "Search anime by title...");
        searchBar.add(m_QueryField, BorderLayout.CENTER);
        searchBar.add(m_SearchButton, BorderLayout.EAST);
        header.add(searchBar, BorderLayout.SOUTH);

        return header;
    }

    private JPanel createResultsPanel() {
        JPanel panel = UITheme.card(new BorderLayout());

        JPanel discovery = UITheme.panel(new BorderLayout(0, 12));
        JPanel discoveryHeader = UITheme.panel(new BorderLayout());

        discoveryHeader.add(UITheme.label("SEARCH RESULTS", 12, true, UITheme.MUTED), BorderLayout.WEST);
        JPanel discoveryActions = UITheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        discoveryActions.add(m_ViewDiscoveredEpisodes);
        discoveryActions.add(m_AddButton);

        discoveryHeader.add(discoveryActions, BorderLayout.EAST);
        discovery.add(discoveryHeader, BorderLayout.NORTH);

        configureAnimeCards(m_AnimeList);

        discovery.add(new JScrollPane(m_AnimeList), BorderLayout.CENTER);
        discovery.add(createPager(m_PreviousResults, m_ResultPage, m_NextResults), BorderLayout.SOUTH);

        JPanel saved = UITheme.panel(new BorderLayout(0, 12));
        JPanel savedHeader = UITheme.panel(new BorderLayout());

        savedHeader.add(UITheme.label("SAVED ANIME", 12, true, UITheme.MUTED), BorderLayout.WEST);
        JPanel savedActions = UITheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        savedActions.add(m_ViewSavedEpisodes);
        savedActions.add(m_RemoveButton);

        savedHeader.add(savedActions, BorderLayout.EAST);
        saved.add(savedHeader, BorderLayout.NORTH);

        configureAnimeCards(m_SavedList);

        saved.add(new JScrollPane(m_SavedList), BorderLayout.CENTER);

        JPanel savedFooter = UITheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        savedFooter.add(m_CacheEpisodesButton);
        saved.add(savedFooter, BorderLayout.SOUTH);

        m_Tabs.addTab("Discover", discovery);
        m_Tabs.addTab("Saved", saved);

        panel.add(m_Tabs, BorderLayout.CENTER);
        return panel;
    }

    private void cacheSelectedEpisodes() {
        Anime anime = m_SavedList.getSelectedValue();
        if (m_Busy || !m_LibraryReady || anime == null)
            return;

        setBusy(true, "Refreshing episode catalog for " + anime.title() + "...");

        new SwingWorker<EpisodeCatalogService.CatalogResult, Integer>() {

            @Override
            protected EpisodeCatalogService.CatalogResult doInBackground() throws IOException, InterruptedException {
                return m_EpisodeCatalogs.refresh(anime.malId(), page -> publish(page));
            }

            @Override
            protected void process(List<Integer> pages) {
                m_Status.setText("Fetching episode page " + pages.get(pages.size() - 1) + " for " + anime.title() + "...");
            }

            @Override
            protected void done() {
                try {
                    EpisodeCatalogService.CatalogResult result = get();
                    m_Status.setText(catalogStatus(result));
                    m_Status.setToolTipText(result.warning());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }

        }.execute();
    }

    private JPanel createEpisodesPanel() {
        JPanel panel = UITheme.card(new BorderLayout(0, 12));
        JPanel heading = UITheme.panel(new BorderLayout(12, 0));

        heading.add(m_EpisodeHeading, BorderLayout.CENTER);
        heading.add(m_BackButton, BorderLayout.EAST);

        panel.add(heading, BorderLayout.NORTH);

        configureList(m_EpisodeList);

        m_EpisodeList.setCellRenderer((list, episode, index, selected, focused) -> {
            String flags = (episode.filler() ? "  ·  Filler" : "") + (episode.recap() ? "  ·  Recap" : "");
            return listLabel("Episode " + episode.number() + " — " + episode.title() + flags, selected);
        });

        panel.add(new JScrollPane(m_EpisodeList), BorderLayout.CENTER);
        panel.add(createPager(m_PreviousEpisodes, m_EpisodePage, m_NextEpisodes), BorderLayout.SOUTH);

        return panel;
    }

    private void configureAnimeCards(JList<Anime> list) {
        list.setBackground(UITheme.CARD);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        list.setVisibleRowCount(-1);
        list.setFixedCellWidth(174);
        list.setFixedCellHeight(274);
        list.setBorder(new EmptyBorder(8, 8, 8, 8));
        list.setCellRenderer(new AnimeCardRenderer(m_Artwork));

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) || event.getClickCount() != 2)
                    return;

                int index = list.locationToIndex(event.getPoint());
                Rectangle bounds = index < 0 ? null : list.getCellBounds(index, index);

                if (bounds != null && bounds.contains(event.getPoint())) {
                    list.setSelectedIndex(index);
                    openSelectedAnime(list);
                }
            }
        });

        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "viewEpisodes");
        list.getActionMap().put("viewEpisodes", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openSelectedAnime(list);
            }
        });
    }

    private void openSelectedAnime(JList<Anime> list) {
        if (m_Busy || m_Tabs.getSelectedIndex() != (list == m_AnimeList ? 0 : 1))
            return;

        Anime anime = list.getSelectedValue();
        if (anime == null)
            return;

        if (list == m_SavedList)
            openSavedEpisodes(anime);
        else
            loadEpisodes(anime, 1);
    }

    private void openSavedEpisodes(Anime anime) {
        if (m_Busy || anime == null)
            return;

        m_SelectedAnime = anime;
        m_CurrentCatalog = null;
        m_UsingLocalCatalog = false;
        m_CurrentEpisodePage = 0;
        m_EpisodeViewDismissed = false;
        m_LoadingSavedEpisodes = true;

        setBusy(true, "Loading saved episodes for " + anime.title() + "...");

        new SwingWorker<EpisodeCatalogService.CatalogResult, CatalogUpdate>() {
            @Override
            protected EpisodeCatalogService.CatalogResult doInBackground() throws IOException, InterruptedException {
                return m_EpisodeCatalogs.getOrRefresh(anime.malId(),
                        catalog -> publish(new CachedEpisodes(catalog)),
                        page -> publish(new RequestedEpisodePage(page)));
            }

            @Override
            protected void process(List<CatalogUpdate> updates) {
                if (!m_LoadingSavedEpisodes)
                    return;

                for (CatalogUpdate update : updates) {
                    if (update instanceof CachedEpisodes(EpisodeCatalog catalog)) {
                        m_CurrentCatalog = catalog;
                        m_UsingLocalCatalog = true;

                        if (!m_EpisodeViewDismissed)
                            showLocalEpisodePage(1);

                        m_Status.setText("Showing saved episodes while checking for updates...");
                    } else if (update instanceof RequestedEpisodePage(int number)) {
                        m_Status.setText("Checking page " + number + " for " + anime.title() + "...");
                    }
                }
            }

            @Override
            protected void done() {
                try {
                    EpisodeCatalogService.CatalogResult result = get();
                    int currentPage = m_UsingLocalCatalog ? m_CurrentEpisodePage : 1;

                    m_CurrentCatalog = result.catalog();
                    m_UsingLocalCatalog = true;

                    if (!m_EpisodeViewDismissed) {
                        int pages = Math.max(1, (m_CurrentCatalog.episodes().size() + LOCAL_EPISODES_PER_PAGE - 1) / LOCAL_EPISODES_PER_PAGE);
                        showLocalEpisodePage(Math.clamp(currentPage, 1, pages));
                    }

                    m_Status.setText(catalogStatus(result));
                    m_Status.setToolTipText(result.warning());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    m_LoadingSavedEpisodes = false;
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private String catalogStatus(EpisodeCatalogService.CatalogResult result) {
        int count = result.catalog().episodes().size();
        String updated = CACHE_DATE_FORMAT.format(result.catalog().fetchedAt());

        return switch (result.source()) {
            case CACHE -> "Loaded " + count + " saved episodes (updated " + updated + ").";
            case NETWORK -> "Updated " + count + " episodes (" + updated + ").";
            case CACHE_FALLBACK -> "Could not refresh. Showing " + count + " saved episodes from " + updated + ".";
        };
    }

    private void changeEpisodePage(int page) {
        if (page < 1 || (m_Busy && !(m_LoadingSavedEpisodes && m_UsingLocalCatalog)))
            return;

        if (m_UsingLocalCatalog) {
            showLocalEpisodePage(page);
            m_Status.setText("Browsing locally cached episodes.");
        } else {
            loadEpisodes(m_SelectedAnime, page);
        }
    }

    private void showLocalEpisodePage(int page) {
        if (m_CurrentCatalog == null || m_SelectedAnime == null || page < 1)
            return;

        List<AnimeEpisode> episodes = m_CurrentCatalog.episodes();
        int totalPages = Math.max(1, (episodes.size() + LOCAL_EPISODES_PER_PAGE - 1) / LOCAL_EPISODES_PER_PAGE);

        if (page > totalPages)
            return;

        int from = (page - 1) * LOCAL_EPISODES_PER_PAGE;
        int to = Math.min(from + LOCAL_EPISODES_PER_PAGE, episodes.size());

        m_EpisodeModel.clear();
        episodes.subList(from, to).forEach(m_EpisodeModel::addElement);

        String name = m_SelectedAnime.title();

        m_EpisodeHeading.setText("EPISODES — " + (name.length() > 40 ? name.substring(0, 37) + "..." : name));
        m_EpisodeHeading.setToolTipText(name);

        m_CurrentEpisodePage = page;
        m_HasMoreEpisodes = page < totalPages;
        m_EpisodePage.setText("Page " + page + " / " + totalPages);

        m_ViewLayout.show(m_Views, "episodes");
        updateButtons();
    }

    private void preloadArtwork(JList<Anime> list, List<Anime> anime) {
        for (Anime entry : anime)
            m_Artwork.load(entry).whenComplete((icon, error) -> SwingUtilities.invokeLater(list::repaint));
    }

    private void loadSavedLibrary() {
        setBusy(true, "Loading your saved anime...");

        new SwingWorker<java.util.List<Anime>, Void>() {
            @Override
            protected List<Anime> doInBackground() throws IOException {
                return m_Library.load();
            }

            @Override
            protected void done() {
                try {
                    get();
                    m_LibraryReady = true;
                    refreshSavedList();
                    m_Status.setText("Saved anime loaded. Search to discover more titles.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void addSelectedAnime() {
        Anime anime = m_AnimeList.getSelectedValue();

        if (!m_LibraryReady || m_Busy || anime == null)
            return;

        setBusy(true, "Saving " + anime.title() + "...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws IOException {
                return m_Library.add(anime);
            }

            @Override
            protected void done() {
                try {
                    boolean added = get();
                    refreshSavedList();

                    m_Status.setText(added
                            ? "Saved " + anime.title() + " to your library."
                            : anime.title() + " is already saved.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void removeSelectedAnime() {
        Anime anime = m_SavedList.getSelectedValue();

        if (!m_LibraryReady || m_Busy || anime == null)
            return;

        setBusy(true, "Removing " + anime.title() + "...");

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws IOException {
                return m_Library.remove(anime.malId());
            }

            @Override
            protected void done() {
                try {
                    boolean removed = get();
                    refreshSavedList();

                    m_Status.setText(removed
                            ? "Removed " + anime.title() + " from your library."
                            : "That anime is no longer in your library.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void refreshSavedList() {
        m_SavedModel.clear();

        List<Anime> saved = m_Library.getAll();
        saved.forEach(m_SavedModel::addElement);

        m_Tabs.setTitleAt(1, "Saved (" + saved.size() + ")");
        preloadArtwork(m_SavedList, saved);
    }

    private void configureList(JList<?> list) {
        list.setBackground(UITheme.CARD);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(38);
        list.setBorder(new EmptyBorder(4, 0, 4, 0));
    }

    private JLabel listLabel(String text, boolean selected) {
        JLabel label = UITheme.label(text, 13, false, UITheme.TEXT);
        label.setOpaque(true);
        label.setBackground(selected ? UITheme.SELECTION : UITheme.CARD);
        label.setBorder(new EmptyBorder(6, 10, 6, 10));
        label.setToolTipText(text);
        return label;
    }

    private JPanel createPager(JButton previous, JLabel indicator, JButton next) {
        JPanel pager = UITheme.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pager.add(previous);
        pager.add(indicator);
        pager.add(next);

        return pager;
    }

    private void search(int page) {
        if (m_Busy || page < 1)
            return;

        String query = page == 1 ? m_QueryField.getText().trim() : m_LastQuery;
        if (query == null || query.isBlank()) {
            m_Status.setText("Enter an anime title to search.");
            return;
        }

        setBusy(true, "Searching the anime catalog...");

        new SwingWorker<Page<Anime>, Void>() {
            @Override
            protected Page<Anime> doInBackground() throws Exception {
                return m_Catalog.searchAnime(query, page);
            }

            @Override
            protected void done() {
                try {
                    Page<Anime> results = get();

                    m_LastQuery = query;
                    m_CurrentResultPage = results.number();
                    m_HasMoreResults = results.hasNextPage();
                    m_ResultPage.setText("Page " + results.number());

                    m_AnimeModel.clear();
                    results.items().forEach(m_AnimeModel::addElement);
                    preloadArtwork(m_AnimeList, results.items());
                    m_AnimeList.clearSelection();

                    m_Tabs.setSelectedIndex(0);
                    m_ViewLayout.show(m_Views, "collection");

                    m_SelectedAnime = null;
                    m_CurrentCatalog = null;
                    m_UsingLocalCatalog = false;

                    m_EpisodeModel.clear();
                    m_EpisodeHeading.setText("EPISODES");
                    m_CurrentEpisodePage = 0;
                    m_HasMoreEpisodes = false;
                    m_EpisodePage.setText("Page —");

                    m_Status.setText(results.items().isEmpty()
                            ? "No matching anime found."
                            : "Select an anime, then choose View episodes (or double-click its card).");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void loadEpisodes(Anime anime, int page) {
        if (m_Busy || anime == null || page < 1)
            return;

        boolean changedAnime = m_SelectedAnime == null || m_SelectedAnime.malId() != anime.malId();
        m_SelectedAnime = anime;

        if (changedAnime) {
            m_EpisodeModel.clear();
            m_CurrentEpisodePage = 0;
            m_EpisodePage.setText("Page —");
            m_HasMoreEpisodes = false;
        }

        setBusy(true, "Loading episodes for " + anime.title() + "...");

        new SwingWorker<Page<AnimeEpisode>, Void>() {
            @Override
            protected Page<AnimeEpisode> doInBackground() throws Exception {
                return m_Catalog.getEpisodes(anime.malId(), page);
            }

            @Override
            protected void done() {
                try {
                    Page<AnimeEpisode> episodes = get();

                    m_SelectedAnime = anime;
                    m_CurrentCatalog = null;
                    m_UsingLocalCatalog = false;

                    m_EpisodeModel.clear();
                    episodes.items().forEach(m_EpisodeModel::addElement);

                    String title = anime.title().length() > 40
                            ? anime.title().substring(0, 37) + "..."
                            : anime.title();

                    m_EpisodeHeading.setText("EPISODES — " + title);
                    m_EpisodeHeading.setToolTipText(anime.title());

                    m_CurrentEpisodePage = episodes.number();
                    m_HasMoreEpisodes = episodes.hasNextPage();
                    m_EpisodePage.setText("Page " + episodes.number());
                    m_ViewLayout.show(m_Views, "episodes");

                    m_Status.setText(episodes.items().isEmpty()
                            ? "No episode details are available for this anime."
                            : "Showing " + episodes.items().size() + " episodes on this page.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError(exception);
                } catch (ExecutionException exception) {
                    showError(exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        m_Busy = busy;

        if (message != null) {
            m_Status.setText(message);
            m_Status.setToolTipText(null);
        }

        updateButtons();
    }

    private void updateButtons() {
        m_SearchButton.setEnabled(!m_Busy);
        m_QueryField.setEnabled(!m_Busy);
        m_AnimeList.setEnabled(!m_Busy);
        m_SavedList.setEnabled(!m_Busy);

        Anime discovered = m_AnimeList.getSelectedValue();

        m_AddButton.setEnabled(!m_Busy && m_LibraryReady && discovered != null && !m_Library.contains(discovered.malId()));
        m_RemoveButton.setEnabled(!m_Busy && m_LibraryReady && m_SavedList.getSelectedValue() != null);

        m_PreviousResults.setEnabled(!m_Busy && m_CurrentResultPage > 1);
        m_NextResults.setEnabled(!m_Busy && m_CurrentResultPage > 0 && m_HasMoreResults);

        boolean localPagingAvailable = !m_Busy || (m_LoadingSavedEpisodes && m_UsingLocalCatalog);

        m_PreviousEpisodes.setEnabled(localPagingAvailable && m_CurrentEpisodePage > 1);
        m_NextEpisodes.setEnabled(localPagingAvailable && m_CurrentEpisodePage > 0 && m_HasMoreEpisodes);

        m_ViewDiscoveredEpisodes.setEnabled(!m_Busy && m_AnimeList.getSelectedValue() != null);
        m_ViewSavedEpisodes.setEnabled(!m_Busy && m_SavedList.getSelectedValue() != null);

        m_CacheEpisodesButton.setEnabled(!m_Busy && m_LibraryReady && m_SavedList.getSelectedValue() != null);
    }

    private void showError(Throwable cause) {
        String message = cause == null ? "Unknown catalog error." : cause.getMessage();
        m_Status.setText("Operation failed: " + message);
    }

}