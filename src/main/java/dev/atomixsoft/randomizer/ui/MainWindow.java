package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.App;
import dev.atomixsoft.randomizer.service.EpisodeFileService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URL;

public final class MainWindow extends JFrame {

    private static final String RANDOMIZER = "randomizer";
    private static final String LIBRARY = "library";
    private static final String ACTIVITY = "activity";

    private final CardLayout m_PageLayout = new CardLayout();
    private final JPanel m_Pages = new JPanel(m_PageLayout);
    private final ButtonGroup m_NavigationGroup = new ButtonGroup();
    private final SessionActivityPanel m_ActivityPanel = new SessionActivityPanel();

    public MainWindow() {
        super("Anime Episode Randomizer");

        m_Pages.add(new RandomizerPanel(new EpisodeFileService(), m_ActivityPanel::addSelection), RANDOMIZER);
        m_Pages.add(createLibraryPage(), LIBRARY);
        m_Pages.add(m_ActivityPanel, ACTIVITY);

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
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(228, 0));
        sidebar.setBackground(UITheme.SIDEBAR);
        sidebar.setBorder(new EmptyBorder(28, 16, 20, 16));

        JPanel top = UITheme.panel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel brand = UITheme.panel(new BorderLayout(12, 0));
        JLabel logo = UITheme.label("A", 24, true, UITheme.ACCENT);
        logo.setHorizontalAlignment(SwingConstants.CENTER);
        logo.setPreferredSize(new Dimension(36, 38));
        brand.add(logo, BorderLayout.WEST);

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
        addNavigation(top, "Session Activity", ACTIVITY, false);

        sidebar.add(top, BorderLayout.NORTH);

        JPanel footer = UITheme.panel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));

        JLabel mode = UITheme.label("LOCAL FILE MODE", 11, true, UITheme.ACCENT);
        JLabel version = UITheme.label("Desktop UI · MS 1.2", 11, false, UITheme.MUTED);

        mode.setAlignmentX(LEFT_ALIGNMENT);
        version.setAlignmentX(LEFT_ALIGNMENT);

        footer.add(mode);
        footer.add(Box.createVerticalStrut(7));
        footer.add(version);

        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
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

        button.addActionListener(event -> m_PageLayout.show(m_Pages, page));

        m_NavigationGroup.add(button);
        parent.add(button);
        parent.add(Box.createVerticalStrut(8));
        button.setSelected(initiallySelected);
    }

    private JPanel createLibraryPage() {
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(UITheme.BACKGROUND);
        page.setBorder(new EmptyBorder(30, 36, 36, 36));

        JPanel layout = UITheme.panel(new BorderLayout(0, 24));

        JPanel header = UITheme.panel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel title = UITheme.label("Anime library", 29, true, UITheme.TEXT);
        JLabel subtitle = UITheme.label("A home for the anime you want in your randomizer pool.",
                                        13, false, UITheme.MUTED);

        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);

        header.add(title);
        header.add(Box.createVerticalStrut(8));
        header.add(subtitle);
        layout.add(header, BorderLayout.NORTH);

        JPanel empty = UITheme.card(new BorderLayout(0, 14));

        empty.add(UITheme.label("Coming soon", 21, true, UITheme.TEXT), BorderLayout.NORTH);
        empty.add(UITheme.label("Search, save, and browse anime once API integration arrives.",
                                13, false, UITheme.MUTED), BorderLayout.CENTER);

        layout.add(empty, BorderLayout.CENTER);
        page.add(layout, BorderLayout.NORTH);
        return page;
    }

}