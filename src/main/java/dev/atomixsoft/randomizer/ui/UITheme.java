package dev.atomixsoft.randomizer.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public final class UITheme {

    public static Color BACKGROUND = new Color(0x14161C);
    public static Color SIDEBAR = new Color(0x1A1C24);
    public static Color CARD = new Color(0x21242E);
    public static Color CARD_BORDER = new Color(0x323744);
    public static Color TEXT = new Color(0xF2F3FA);
    public static Color MUTED = new Color(0xA1A6B8);
    public static Color ACCENT = new Color(0x7869EF);
    public static Color SELECTION = new Color(0x373157);

    public static void applyPalette(boolean light) {
        if (light) {
            BACKGROUND = new Color(0xF3F4F8);
            SIDEBAR = new Color(0xE8EAF2);
            CARD = new Color(0xFFFFFF);
            CARD_BORDER = new Color(0xD6D9E5);
            TEXT = new Color(0x212334);
            MUTED = new Color(0x585F73);
            ACCENT = new Color(0x5142BC);
            SELECTION = new Color(0xDCD8FA);
        } else {
            BACKGROUND = new Color(0x14161C);
            SIDEBAR = new Color(0x1A1C24);
            CARD = new Color(0x21242E);
            CARD_BORDER = new Color(0x323744);
            TEXT = new Color(0xF2F3FA);
            MUTED = new Color(0xA1A6B8);
            ACCENT = new Color(0x7869EF);
            SELECTION = new Color(0x373157);
        }
    }

    private UITheme() {

    }

    public static void configure() {
        UIManager.put("Button.arc", 16);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.width", 11);
    }

    public static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        Font base = UIManager.getFont("Label.font");

        if(base == null)
            base = new Font(Font.DIALOG, Font.PLAIN, size);

        label.setFont(base.deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        label.setForeground(color);

        return label;
    }

    public static JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);

        return panel;
    }

    public static JPanel panel() {
        return panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    }

    public static JPanel card(LayoutManager layout) {
        JPanel card = new RoundedCard(layout);
        card.setBorder(new EmptyBorder(22, 24, 22, 24));

        return card;
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);

        button.putClientProperty("JButton.buttonType", "roundRect");
        button.putClientProperty("JComponent.minimumHeight", 42);
        button.setFocusPainted(true);
        button.setBackground(primary ? ACCENT : CARD_BORDER);
        button.setForeground(primary ? Color.WHITE : TEXT);
        button.setMargin(new java.awt.Insets(10, 18, 10, 18));

        return button;
    }

    private static final class RoundedCard extends JPanel {

        private RoundedCard(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();

            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(CARD);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);

                g.setColor(CARD_BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            } finally {
                g.dispose();
            }

            super.paintComponent(graphics);
        }

    }

}
