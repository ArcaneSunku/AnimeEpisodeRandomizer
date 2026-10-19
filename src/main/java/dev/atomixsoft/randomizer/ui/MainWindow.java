package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.App;
import dev.atomixsoft.randomizer.api.AnimeCatalogClient;
import dev.atomixsoft.randomizer.api.tenrai.TenraiCatalogClient;
import dev.atomixsoft.randomizer.cache.AnimeArtworkCache;
import dev.atomixsoft.randomizer.persistence.AnimeLibraryRepository;
import dev.atomixsoft.randomizer.persistence.ApplicationPaths;
import dev.atomixsoft.randomizer.persistence.JsonAnimeLibraryRepository;
import dev.atomixsoft.randomizer.persistence.JsonEpisodeCatalogRepository;
import dev.atomixsoft.randomizer.persistence.JsonSelectionHistoryRepository;
import dev.atomixsoft.randomizer.service.AnimeLibraryService;
import dev.atomixsoft.randomizer.service.EpisodeCatalogService;
import dev.atomixsoft.randomizer.service.EpisodeRandomizerService;
import dev.atomixsoft.randomizer.service.SelectionHistoryService;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

public final class MainWindow extends JFrame {

    private static final String RANDOMIZER = "randomizer";
    private static final String LIBRARY = "library";
    private static final String HISTORY = "history";

    private final CardLayout m_PageLayout = new CardLayout();
    private final JPanel m_Pages = new JPanel(m_PageLayout);
    private final ButtonGroup m_NavigationGroup = new ButtonGroup();
    private final HistoryPanel m_HistoryPanel;
    private final RandomizerPanel m_RandomizerPanel;
    private final AnimeArtworkCache m_ArtworkCache = new AnimeArtworkCache(ApplicationPaths.cacheDirectory().resolve("artwork"));

    public MainWindow() {
        super("Anime Episode Randomizer");

        AnimeLibraryRepository savedLibrary = new JsonAnimeLibraryRepository(
                ApplicationPaths.dataDirectory().resolve("library.json"));
        JsonEpisodeCatalogRepository episodeRepository = new JsonEpisodeCatalogRepository(
                ApplicationPaths.cacheDirectory().resolve("episode-catalogs"));
        JsonSelectionHistoryRepository historyRepository = new JsonSelectionHistoryRepository(
                ApplicationPaths.dataDirectory().resolve("history.json"));
        SelectionHistoryService history = new SelectionHistoryService(historyRepository);
        EpisodeRandomizerService randomizer = new EpisodeRandomizerService(savedLibrary, episodeRepository, historyRepository);
        m_RandomizerPanel = new RandomizerPanel(savedLibrary, randomizer, m_ArtworkCache,
                history, selection -> refreshHistory());
        m_HistoryPanel = new HistoryPanel(history, m_RandomizerPanel::historyChanged);
        m_Pages.add(m_RandomizerPanel, RANDOMIZER);
        AnimeCatalogClient catalog = new TenraiCatalogClient();

        EpisodeCatalogService episodeCatalogs = new EpisodeCatalogService(catalog, episodeRepository);

        m_Pages.add(new AnimeLibraryPanel(catalog,
                new AnimeLibraryService(savedLibrary),
                m_ArtworkCache, episodeCatalogs), LIBRARY);

        m_Pages.add(m_HistoryPanel, HISTORY);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UITheme.BACKGROUND);
        root.add(createSidebar(), BorderLayout.WEST);
        root.add(m_Pages, BorderLayout.CENTER);

        setContentPane(root);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 620));
        setSize(1080, 760);
        setLocationByPlatform(true);

        URL icon = App.class.getResource("/images/icon.gif");

        if (icon != null)
            setIconImage(new javax.swing.ImageIcon(icon).getImage());

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                m_ArtworkCache.close();
            }
        });
    }

    private void refreshHistory() {
        m_HistoryPanel.refresh();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(228, 0));
        sidebar.setBackground(UITheme.SIDEBAR);
        sidebar.setBorder(new EmptyBorder(28, 16, 20, 16));

        JPanel top = UITheme.panel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel brand = UITheme.panel(new BorderLayout(12, 0));
        brand.add(createLogo(), BorderLayout.WEST);

        JPanel brandText = UITheme.panel();
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.add(UITheme.label("ANIME", 15, true, UITheme.TEXT));
        brandText.add(UITheme.label("RANDOMIZER", 12, true, UITheme.MUTED));

        brand.add(brandText, BorderLayout.CENTER);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        top.add(brand);
        top.add(Box.createVerticalStrut(45));

        JLabel navLabel = UITheme.label("WORKSPACE", 11, true, UITheme.MUTED);
        navLabel.setBorder(new EmptyBorder(0, 12, 10, 0));
        navLabel.setAlignmentX(LEFT_ALIGNMENT);
        top.add(navLabel);

        addNavigation(top, "Randomizer", RANDOMIZER, true);
        addNavigation(top, "Anime Library", LIBRARY, false);
        addNavigation(top, "History & Sessions", HISTORY, false);

        sidebar.add(top, BorderLayout.NORTH);

        JPanel footer = UITheme.panel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JLabel mode = UITheme.label("CATALOG ENABLED", 11, true, UITheme.ACCENT);
        JLabel provider = UITheme.label("Powered by Tenrai", 11, false, UITheme.MUTED);

        mode.setAlignmentX(LEFT_ALIGNMENT);
        provider.setAlignmentX(LEFT_ALIGNMENT);

        footer.add(mode);
        footer.add(Box.createVerticalStrut(7));
        footer.add(provider);

        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    private JLabel createLogo() {
        URL resource = App.class.getResource("/images/icon.png");
        if (resource == null)
            throw new IllegalStateException("Application icon resource could not be found.");

        try {
            BufferedImage original = ImageIO.read(resource);
            if (original == null)
                throw new IllegalStateException("Application icon could not be decoded.");

            int size = 48;
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = scaled.createGraphics();

            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                graphics.drawImage(original, 0, 0, size, size, null);
            } finally {
                graphics.dispose();
            }

            JLabel logo = new JLabel(new ImageIcon(scaled));
            logo.setPreferredSize(new Dimension(size, size));

            return logo;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load application icon.", exception);
        }
    }

    private void addNavigation(JPanel parent, String label, String page, boolean initiallySelected) {
        JToggleButton button = new JToggleButton(label);

        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setPreferredSize(new Dimension(190, 44));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setForeground(UITheme.TEXT);
        button.setBackground(UITheme.SIDEBAR);

        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("FlatLaf.style", "arc: 14; focusWidth: 1; selectedBackground: #342f59; selectedForeground: #f2f3fa");

        button.addActionListener(event -> {
            if (RANDOMIZER.equals(page))
                m_RandomizerPanel.reloadLibrary();
            if (HISTORY.equals(page))
                m_HistoryPanel.refresh();
            m_PageLayout.show(m_Pages, page);
        });

        m_NavigationGroup.add(button);
        parent.add(button);
        parent.add(Box.createVerticalStrut(8));
        button.setSelected(initiallySelected);
    }

}