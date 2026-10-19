package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.model.SelectionRecord;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SessionActivityPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")
            .withZone(ZoneId.systemDefault());

    private final JPanel m_Entries = UITheme.panel();
    private final JLabel m_EmptyMessage = UITheme.label("No episodes selected during this session yet.",
            15, false, UITheme.MUTED);
    private int m_SelectionCount;

    public SessionActivityPanel() {
        super(new BorderLayout());
        setBackground(UITheme.BACKGROUND);

        JPanel content = UITheme.panel(new BorderLayout(0, 24));
        content.setBorder(new EmptyBorder(30, 36, 36, 36));

        JPanel heading = UITheme.panel();
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        JLabel title = UITheme.label("Session activity", 29, true, UITheme.TEXT);
        JLabel subtitle = UITheme.label("Selections from this run. Duplicate prevention persists after restarting.",
                13, false, UITheme.MUTED);
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        heading.add(title);
        heading.add(Box.createVerticalStrut(8));
        heading.add(subtitle);
        content.add(heading, BorderLayout.NORTH);

        m_Entries.setLayout(new BoxLayout(m_Entries, BoxLayout.Y_AXIS));
        JPanel list = UITheme.panel(new BorderLayout());
        list.add(m_Entries, BorderLayout.NORTH);
        list.add(m_EmptyMessage, BorderLayout.CENTER);
        content.add(list, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(UITheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
    }

    public void addSelection(SelectionRecord selection) {
        if (m_SelectionCount++ == 0)
            m_EmptyMessage.setVisible(false);

        JPanel entry = UITheme.card(new BorderLayout(16, 0));
        entry.setAlignmentX(LEFT_ALIGNMENT);
        entry.setMaximumSize(new Dimension(Integer.MAX_VALUE, 98));

        JPanel info = UITheme.panel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        JLabel episode = UITheme.label("Episode " + selection.episodeNumber() + " — " + selection.episodeTitle(),
                15, true, UITheme.TEXT);
        JLabel anime = UITheme.label(selection.animeTitle(), 12, false, UITheme.MUTED);

        episode.setAlignmentX(LEFT_ALIGNMENT);
        anime.setAlignmentX(LEFT_ALIGNMENT);
        info.add(episode);
        info.add(Box.createVerticalStrut(7));
        info.add(anime);

        entry.add(info, BorderLayout.CENTER);
        entry.add(UITheme.label(TIME_FORMAT.format(selection.selectedAt()), 12, false, UITheme.MUTED), BorderLayout.EAST);

        if (m_SelectionCount > 1)
            m_Entries.add(Box.createVerticalStrut(12), 0);

        m_Entries.add(entry, 0);
        m_Entries.revalidate();
        m_Entries.repaint();
    }

}
