package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.App;
import dev.atomixsoft.randomizer.service.EpisodeFileService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

public final class MainWindow extends JFrame {

    private final EpisodeFileService m_EpisodeFiles = new EpisodeFileService();
    private final JLabel m_FileName = new JLabel("No episode list loaded");
    private final JLabel m_RemainingCount = new JLabel("0 episodes available");
    private final JLabel m_Selection = new JLabel("—", SwingConstants.CENTER);
    private final JLabel m_SelectionStatus = new JLabel("Choose an episode list to begin", SwingConstants.CENTER);
    private final JTextArea m_ActivityLog = new JTextArea(5, 30);
    private final JButton m_SelectButton = new JButton("Choose Random Episode");

    public MainWindow() {
        super("Anime Episode Randomizer");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(createInterface());
        setMinimumSize(new Dimension(760, 580));
        setSize(1020, 700);
        setLocationByPlatform(true);

        URL icon = App.class.getResource("/images/icon.gif");
        if (icon != null)
            setIconImage(new javax.swing.ImageIcon(icon).getImage());
    }

    private JPanel createInterface() {
        JPanel root = new JPanel(new BorderLayout(20, 0));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        root.add(createSidebar(), BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.add(createHeader(), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.add(createSourceCard(), BorderLayout.NORTH);
        body.add(createSelectionCard(), BorderLayout.CENTER);
        body.add(createActivityCard(), BorderLayout.SOUTH);

        content.add(body, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);
        return root;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(180, 0));
        sidebar.setBorder(new EmptyBorder(12, 4, 0, 12));

        JLabel title = new JLabel("ANIME RANDOMIZER");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        title.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(title);
        sidebar.add(Box.createVerticalStrut(36));

        JButton randomizer = new JButton("Randomizer");
        randomizer.setEnabled(false);
        randomizer.setToolTipText("Current page");
        addNavigationButton(sidebar, randomizer);

        JButton library = new JButton("Anime Library — Soon");
        library.setEnabled(false);
        addNavigationButton(sidebar, library);

        JButton history = new JButton("History — Soon");
        history.setEnabled(false);
        addNavigationButton(sidebar, history);

        sidebar.add(Box.createVerticalGlue());

        JLabel version = new JLabel("Desktop foundation · WIP");
        version.setFont(version.getFont().deriveFont(11f));
        version.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(version);
        return sidebar;
    }

    private void addNavigationButton(JPanel sidebar, JButton button) {
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        button.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(8));
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new GridLayout(2, 1, 0, 4));

        JLabel title = new JLabel("Episode Randomizer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 27f));
        header.add(title);
        header.add(new JLabel("Pick something to watch. The anime catalog comes next."));

        return header;
    }

    private JPanel createSourceCard() {
        JPanel card = createCard(new BorderLayout(16, 0));
        JPanel info = new JPanel(new GridLayout(3, 1, 0, 5));

        info.add(new JLabel("CURRENT EPISODE SOURCE"));
        info.add(m_FileName);
        info.add(m_RemainingCount);
        card.add(info, BorderLayout.CENTER);

        JButton openButton = new JButton("Open Episode List...");
        openButton.addActionListener(event -> openEpisodeList());
        card.add(openButton, BorderLayout.EAST);
        return card;
    }

    private JPanel createSelectionCard() {
        JPanel card = createCard(new BorderLayout(0, 16));

        JLabel caption = new JLabel("YOUR RANDOM EPISODE", SwingConstants.CENTER);
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, 13f));
        card.add(caption, BorderLayout.NORTH);

        JPanel middle = new JPanel(new GridLayout(2, 1, 0, 8));
        m_Selection.setFont(m_Selection.getFont().deriveFont(Font.BOLD, 48f));
        middle.add(m_Selection);
        middle.add(m_SelectionStatus);
        card.add(middle, BorderLayout.CENTER);

        JPanel footer = new JPanel(new GridLayout(2, 1, 0, 10));

        m_SelectButton.setEnabled(false);
        m_SelectButton.addActionListener(event -> chooseEpisode());
        footer.add(m_SelectButton);

        JLabel warning = new JLabel(
                "For now, choosing an episode removes it from the source text file.",
                SwingConstants.CENTER
        );
        warning.setFont(warning.getFont().deriveFont(11f));
        footer.add(warning);

        card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createActivityCard() {
        JPanel card = createCard(new BorderLayout(0, 10));
        card.add(new JLabel("ACTIVITY"), BorderLayout.NORTH);

        m_ActivityLog.setEditable(false);
        m_ActivityLog.setLineWrap(true);
        m_ActivityLog.setWrapStyleWord(true);
        m_ActivityLog.append("Ready. Open a text file containing episode numbers.\n");

        card.add(new JScrollPane(m_ActivityLog), BorderLayout.CENTER);
        return card;
    }

    private JPanel createCard(java.awt.LayoutManager layout) {
        JPanel card = new JPanel(layout);
        Color border = UIManager.getColor("Component.borderColor");

        if (border == null)
            border = UIManager.getColor("Separator.foreground");

        if (border == null)
            border = Color.GRAY;

        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border),
                new EmptyBorder(16, 18, 16, 18)
        ));

        return card;
    }

    private void openEpisodeList() {
        JFileChooser chooser = new JFileChooser(Path.of(System.getProperty("user.home")).toFile());
        chooser.setDialogTitle("Open Episode List");
        chooser.setFileFilter(new FileNameExtensionFilter("Text files (*.txt)", "txt"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        Path file = chooser.getSelectedFile().toPath();

        try {
            int count = m_EpisodeFiles.load(file);

            m_FileName.setText(file.getFileName().toString());
            m_FileName.setToolTipText(file.toAbsolutePath().toString());
            m_Selection.setText("—");
            m_SelectionStatus.setText("Ready for a new selection");

            updateRemainingCount();
            appendActivity("Loaded " + file.getFileName() + " (" + count + " episodes)");
        } catch (IOException exception) {
            showError("Could not load the episode list", exception);
        }
    }

    private void chooseEpisode() {
        try {
            int episode = m_EpisodeFiles.chooseRandomEpisode();

            m_Selection.setText("Episode " + episode);
            m_SelectionStatus.setText("Selected from your current list");

            updateRemainingCount();
            appendActivity("Selected episode " + episode);
        } catch (IOException exception) {
            showError("Could not save the episode selection", exception);
        }
    }

    private void updateRemainingCount() {
        int remaining = m_EpisodeFiles.getRemainingCount();

        m_RemainingCount.setText(
                remaining + (remaining == 1 ? " episode available" : " episodes available")
        );

        m_SelectButton.setEnabled(remaining > 0);

        if (remaining == 0)
            m_SelectionStatus.setText("No episodes remaining in this list");
    }

    private void appendActivity(String message) {
        m_ActivityLog.append(message + System.lineSeparator());
        m_ActivityLog.setCaretPosition(m_ActivityLog.getDocument().getLength());
    }

    private void showError(String message, IOException exception) {
        appendActivity(message + ": " + exception.getMessage());
        JOptionPane.showMessageDialog(
                this, exception.getMessage(), message, JOptionPane.ERROR_MESSAGE
        );
    }

}