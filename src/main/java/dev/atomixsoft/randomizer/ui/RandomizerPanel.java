package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.service.EpisodeFileService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class RandomizerPanel extends JPanel {

    public record Selection(Path sourceFile, int episodeNumber, int remainingCount) {
    }

    private final EpisodeFileService m_EpisodeFiles;
    private final Consumer<Selection> m_OnSelection;

    private final JLabel m_SourceName = UITheme.label("No file selected", 17, true, UITheme.TEXT);
    private final JLabel m_SourcePath = UITheme.label("Select a text file containing episode numbers.", 12, false, UITheme.MUTED);
    private final JLabel m_AvailableCount = UITheme.label("0", 30, true, UITheme.TEXT);
    private final JLabel m_SessionCount = UITheme.label("0", 30, true, UITheme.TEXT);
    private final JLabel m_Result = UITheme.label("—", 58, true, UITheme.TEXT);
    private final JLabel m_Feedback = UITheme.label("Load a list to start randomizing.", 14, false, UITheme.MUTED);
    private final JButton m_OpenButton = UITheme.button("Open episode list", false);
    private final JButton m_SelectButton = UITheme.button("Randomize episode", true);

    private Path m_SourceFile;
    private int m_PicksThisSession;
    private boolean m_Busy;

    public RandomizerPanel(EpisodeFileService episodeFiles, Consumer<Selection> onSelection) {
        super(new BorderLayout());

        m_EpisodeFiles = episodeFiles;
        m_OnSelection = onSelection;

        setOpaque(true);
        setBackground(UITheme.BACKGROUND);

        JPanel content = UITheme.panel(new GridBagLayout());
        content.setBorder(new EmptyBorder(30, 36, 36, 36));

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.NORTH;

        addSection(content, createHeader(), constraints, 0, 24);
        addSection(content, createSourceCard(), constraints, 1, 16);
        addSection(content, createStatistics(), constraints, 2, 16);
        addSection(content, createSelectionCard(), constraints, 3, 0);

        constraints.gridy = 4;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.BOTH;
        content.add(Box.createGlue(), constraints);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(UITheme.BACKGROUND);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);
        add(scrollPane, BorderLayout.CENTER);

        m_OpenButton.addActionListener(event -> openEpisodeList());
        m_SelectButton.addActionListener(event -> chooseEpisode());
        updateButtons();
    }

    private void addSection(JPanel parent, JPanel child, GridBagConstraints constraints, int row, int bottomGap) {
        constraints.gridy = row;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, bottomGap, 0);

        parent.add(child, constraints);
    }

    private JPanel createHeader() {
        JPanel header = UITheme.panel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel eyebrow = UITheme.label("RANDOMIZER  /  LOCAL MODE", 11, true, UITheme.ACCENT);
        JLabel title = UITheme.label("Find your next episode", 29, true, UITheme.TEXT);
        JLabel subtitle = UITheme.label("Your anime collection and online episode catalog are coming next.",
                                        13, false, UITheme.MUTED);

        for (JLabel label : new JLabel[]{eyebrow, title, subtitle})
            label.setAlignmentX(LEFT_ALIGNMENT);

        header.add(eyebrow);
        header.add(Box.createVerticalStrut(9));
        header.add(title);
        header.add(Box.createVerticalStrut(6));
        header.add(subtitle);

        return header;
    }

    private JPanel createSourceCard() {
        JPanel card = UITheme.card(new BorderLayout(16, 0));
        JPanel description = UITheme.panel();
        description.setLayout(new BoxLayout(description, BoxLayout.Y_AXIS));

        JLabel label = UITheme.label("EPISODE SOURCE", 11, true, UITheme.MUTED);

        for (JLabel item : new JLabel[]{label, m_SourceName, m_SourcePath})
            item.setAlignmentX(LEFT_ALIGNMENT);

        description.add(label);
        description.add(Box.createVerticalStrut(10));
        description.add(m_SourceName);
        description.add(Box.createVerticalStrut(5));
        description.add(m_SourcePath);

        card.add(description, BorderLayout.CENTER);
        card.add(m_OpenButton, BorderLayout.EAST);

        return card;
    }

    private JPanel createStatistics() {
        JPanel stats = UITheme.panel(new GridLayout(1, 2, 16, 0));
        stats.add(createStatistic("AVAILABLE EPISODES", m_AvailableCount, "In your current text file"));
        stats.add(createStatistic("PICKS THIS SESSION", m_SessionCount, "Since opening the application"));

        return stats;
    }

    private JPanel createStatistic(String name, JLabel value, String hint) {
        JPanel card = UITheme.card(new BorderLayout(0, 8));
        card.add(UITheme.label(name, 11, true, UITheme.MUTED), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        card.add(UITheme.label(hint, 12, false, UITheme.MUTED), BorderLayout.SOUTH);

        return card;
    }

    private JPanel createSelectionCard() {
        JPanel card = UITheme.card(new BorderLayout(0, 24));
        JPanel result = UITheme.panel();
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));

        JLabel heading = UITheme.label("YOUR RANDOM EPISODE", 11, true, UITheme.MUTED);
        heading.setAlignmentX(CENTER_ALIGNMENT);

        m_Result.setHorizontalAlignment(SwingConstants.CENTER);
        m_Result.setAlignmentX(CENTER_ALIGNMENT);
        m_Feedback.setHorizontalAlignment(SwingConstants.CENTER);
        m_Feedback.setAlignmentX(CENTER_ALIGNMENT);

        result.add(heading);
        result.add(Box.createVerticalStrut(18));
        result.add(m_Result);
        result.add(Box.createVerticalStrut(12));
        result.add(m_Feedback);
        card.add(result, BorderLayout.CENTER);

        JPanel actions = UITheme.panel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));

        m_SelectButton.setAlignmentX(CENTER_ALIGNMENT);
        m_SelectButton.setMaximumSize(new Dimension(330, 46));
        actions.add(m_SelectButton);
        actions.add(Box.createVerticalStrut(14));

        JLabel notice = UITheme.label("Legacy mode removes selected numbers from the source .txt file.",
                                      11, false, UITheme.MUTED);

        notice.setHorizontalAlignment(SwingConstants.CENTER);
        notice.setAlignmentX(CENTER_ALIGNMENT);
        actions.add(notice);

        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private void openEpisodeList() {
        JFileChooser chooser = new JFileChooser(Path.of(System.getProperty("user.home")).toFile());
        chooser.setDialogTitle("Open episode list");
        chooser.setFileFilter(new FileNameExtensionFilter("Episode lists (*.txt)", "txt"));

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        Path file = chooser.getSelectedFile().toPath();
        setBusy(true, "Loading episode list...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws IOException {
                return m_EpisodeFiles.load(file);
            }

            @Override
            protected void done() {
                try {
                    int count = get();
                    m_SourceFile = file.toAbsolutePath().normalize();

                    String name = m_SourceFile.getFileName().toString();
                    m_SourceName.setText(name.length() > 35 ? name.substring(0, 32) + "..." : name);
                    m_SourcePath.setText("From " + shortenedPath(m_SourceFile));
                    m_SourcePath.setToolTipText(m_SourceFile.toString());

                    m_Result.setText("—");
                    m_AvailableCount.setText(Integer.toString(count));
                    m_Feedback.setText(count > 0 ? "Ready when you are." : "This list has no remaining episodes.");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Loading interrupted", exception);
                } catch (ExecutionException exception) {
                    showError("Could not load the episode list", exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private String shortenedPath(Path path) {
        String location = path.toString();
        return location.length() > 55 ? "..." + location.substring(location.length() - 52) : location;
    }

    private void chooseEpisode() {
        setBusy(true, "Choosing and saving an episode...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws IOException {
                return m_EpisodeFiles.chooseRandomEpisode();
            }

            @Override
            protected void done() {
                try {
                    int episode = get();
                    int remaining = m_EpisodeFiles.getRemainingCount();

                    m_PicksThisSession++;
                    m_Result.setText("Episode " + episode);
                    m_AvailableCount.setText(Integer.toString(remaining));
                    m_SessionCount.setText(Integer.toString(m_PicksThisSession));
                    m_Feedback.setText(remaining == 0 ? "You have reached the end of this list." : "Enjoy your episode!");

                    m_OnSelection.accept(new Selection(m_SourceFile, episode, remaining));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showError("Selection interrupted", exception);
                } catch (ExecutionException exception) {
                    showError("Could not save the episode selection", exception.getCause());
                } finally {
                    setBusy(false, null);
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        m_Busy = busy;

        if (message != null)
            m_Feedback.setText(message);

        updateButtons();
    }

    private void updateButtons() {
        m_OpenButton.setEnabled(!m_Busy);
        m_SelectButton.setEnabled(!m_Busy && m_SourceFile != null && m_EpisodeFiles.getRemainingCount() > 0);
    }

    private void showError(String title, Throwable cause) {
        String message = cause == null ? "An unknown error occurred." : cause.getMessage();
        m_Feedback.setText("Something went wrong. Try again.");
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

}