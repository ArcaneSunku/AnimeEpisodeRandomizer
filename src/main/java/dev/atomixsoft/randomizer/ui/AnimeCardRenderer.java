package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.cache.AnimeArtworkCache;
import dev.atomixsoft.randomizer.model.Anime;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class AnimeCardRenderer extends JPanel implements ListCellRenderer<Anime> {

    private static final Color SELECTION = new Color(0x373157);

    private final AnimeArtworkCache m_Artwork;
    private final JLabel m_Cover = new JLabel("NO COVER", JLabel.CENTER);
    private final JTextArea m_Title = new JTextArea(2, 13);
    private final JLabel m_Details = UITheme.label("", 11, false, UITheme.MUTED);

    private Color m_Background = UITheme.CARD;

    public AnimeCardRenderer(AnimeArtworkCache artwork) {
        super(new BorderLayout(0, 8));

        m_Artwork = artwork;
        setOpaque(false);
        setBorder(new EmptyBorder(12, 12, 12, 12));

        m_Cover.setOpaque(true);
        m_Cover.setBackground(UITheme.CARD_BORDER);
        m_Cover.setForeground(UITheme.MUTED);
        m_Cover.setPreferredSize(new Dimension(116, 166));

        add(m_Cover, BorderLayout.CENTER);

        JPanel details = UITheme.panel(new BorderLayout(0, 4));

        m_Title.setEditable(false);
        m_Title.setFocusable(false);
        m_Title.setOpaque(false);
        m_Title.setLineWrap(true);
        m_Title.setWrapStyleWord(true);
        m_Title.setFont(UITheme.label("", 13, true, UITheme.TEXT).getFont());
        m_Title.setForeground(UITheme.TEXT);
        m_Title.setPreferredSize(new Dimension(146, 36));

        details.add(m_Title, BorderLayout.CENTER);
        details.add(m_Details, BorderLayout.SOUTH);

        add(details, BorderLayout.SOUTH);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Anime> list, Anime anime, int index, boolean selected, boolean focused) {
        m_Background = selected ? SELECTION : UITheme.CARD;

        ImageIcon icon = m_Artwork.getIfReady(anime);
        m_Cover.setIcon(icon);
        m_Cover.setText(icon == null ? "NO COVER" : "");

        m_Title.setText(anime.title());

        String type = anime.type() == null || anime.type().isBlank() ? "Anime" : anime.type();
        String count = anime.episodeCount() == null ? "? episodes" : anime.episodeCount() + " episodes";

        m_Details.setText(type + " · " + count);
        setToolTipText(anime.title());

        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();

        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(m_Background);
            g.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 18, 18);

            g.setColor(UITheme.CARD_BORDER);
            g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 18, 18);
        } finally {
            g.dispose();
        }

        super.paintComponent(graphics);
    }

}