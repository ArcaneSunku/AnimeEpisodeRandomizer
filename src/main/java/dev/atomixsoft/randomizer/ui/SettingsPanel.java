package dev.atomixsoft.randomizer.ui;

import dev.atomixsoft.randomizer.cache.CacheMaintenanceService;
import dev.atomixsoft.randomizer.persistence.ApplicationPaths;
import dev.atomixsoft.randomizer.settings.AppSettings;
import dev.atomixsoft.randomizer.settings.JsonSettingsRepository;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class SettingsPanel extends JPanel {
    private final JsonSettingsRepository m_Repository;
    private final CacheMaintenanceService m_Cache;
    private final Consumer<AppSettings> m_OnSaved;

    private final JComboBox<AppSettings.Theme> m_Theme = new JComboBox<>(AppSettings.Theme.values());
    private final JCheckBox m_ExcludeFiller = new JCheckBox("Exclude filler by default");
    private final JCheckBox m_ExcludeRecaps = new JCheckBox("Exclude recaps by default");
    private final JComboBox<AppSettings.GroupWeighting> m_Weighting =
            new JComboBox<>(AppSettings.GroupWeighting.values());
    private final JButton m_Save = UITheme.button("Save preferences", true);
    private final JButton m_Defaults = UITheme.button("Restore defaults", false);
    private final JButton m_ClearArtwork = UITheme.button("Clear artwork", false);
    private final JButton m_ClearEpisodes = UITheme.button("Clear episode catalogs", false);
    private final JLabel m_Status = UITheme.label("Changes to appearance take effect after restarting.",
            12, false, UITheme.MUTED);

    private AppSettings m_Saved;
    private boolean m_Busy;

    public SettingsPanel(JsonSettingsRepository repository, AppSettings initial,
                         CacheMaintenanceService cache, Consumer<AppSettings> onSaved) {
        super(new BorderLayout());
        m_Repository = repository;
        m_Cache = cache;
        m_OnSaved = onSaved;
        m_Saved = initial;
        setBackground(UITheme.BACKGROUND);
        setValues(initial);

        JPanel content = UITheme.panel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(25, 30, 30, 30));
        addSection(content, createHeader());
        addSection(content, createPreferences());
        addSection(content, createStorage());
        content.add(m_Status);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(UITheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);

        m_Save.addActionListener(event -> save());
        m_Defaults.addActionListener(event -> {
            setValues(AppSettings.defaults());
            m_Status.setText("Default values loaded. Save preferences to keep them.");
        });
        m_ClearArtwork.addActionListener(event -> clear(CacheMaintenanceService.CacheType.ARTWORK));
        m_ClearEpisodes.addActionListener(event -> clear(CacheMaintenanceService.CacheType.EPISODE_CATALOGS));
    }

    private JPanel createHeader() {
        JPanel panel = UITheme.panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel title = UITheme.label("Settings", 28, true, UITheme.TEXT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        panel.add(UITheme.label("Preferences and locally stored data.", 13, false, UITheme.MUTED));
        return panel;
    }

    private JPanel createPreferences() {
        JPanel card = UITheme.card(new BorderLayout(0, 18));
        card.add(UITheme.label("PREFERENCES", 12, true, UITheme.MUTED), BorderLayout.NORTH);

        JPanel fields = UITheme.panel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        addField(fields, "Appearance", m_Theme);
        fields.add(m_ExcludeFiller);
        fields.add(Box.createVerticalStrut(9));
        fields.add(m_ExcludeRecaps);
        fields.add(Box.createVerticalStrut(15));
        addField(fields, "Group randomization weighting", m_Weighting);
        fields.add(UITheme.label("Randomization starts with one selected anime. Group mode is optional.",
                12, false, UITheme.MUTED));
        card.add(fields, BorderLayout.CENTER);
        JPanel buttons = UITheme.panel(new GridLayout(1, 2, 10, 0));
        buttons.add(m_Defaults);
        buttons.add(m_Save);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createStorage() {
        JPanel card = UITheme.card(new BorderLayout(0, 14));
        card.add(UITheme.label("LOCAL STORAGE", 12, true, UITheme.MUTED), BorderLayout.NORTH);
        JPanel body = UITheme.panel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        addPath(body, "User data (library, history, settings)", ApplicationPaths.dataDirectory().toString());
        body.add(Box.createVerticalStrut(12));
        addPath(body, "Replaceable cache", ApplicationPaths.cacheDirectory().toString());
        body.add(Box.createVerticalStrut(16));
        JTextArea explanation = new JTextArea(
                "Clearing episode catalogs removes offline episode data until you download it again.", 2, 42);
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setOpaque(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setForeground(UITheme.MUTED);
        explanation.setFont(UITheme.label("", 12, false, UITheme.MUTED).getFont());
        explanation.setAlignmentX(LEFT_ALIGNMENT);
        body.add(explanation);
        body.add(Box.createVerticalStrut(12));
        JPanel actions = UITheme.panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.add(m_ClearArtwork);
        actions.add(m_ClearEpisodes);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        body.add(actions);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void addPath(JPanel parent, String caption, String value) {
        JLabel heading = UITheme.label(caption, 12, true, UITheme.TEXT);
        heading.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(heading);
        JTextField location = new JTextField(value);
        location.setEditable(false);
        location.setForeground(UITheme.MUTED);
        location.setBackground(UITheme.CARD);
        location.setBorder(null);
        location.setToolTipText(value);
        location.setFont(UITheme.label("", 11, false, UITheme.MUTED).getFont());
        location.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        location.setAlignmentX(LEFT_ALIGNMENT);
        parent.add(location);
    }

    private void addField(JPanel parent, String title, JComboBox<?> field) {
        JLabel label = UITheme.label(title, 12, true, UITheme.TEXT);
        label.setAlignmentX(LEFT_ALIGNMENT);
        field.setAlignmentX(LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(280, 36));
        parent.add(label);
        parent.add(Box.createVerticalStrut(6));
        parent.add(field);
        parent.add(Box.createVerticalStrut(16));
    }

    private void addSection(JPanel parent, JPanel section) {
        section.setAlignmentX(LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, section.getPreferredSize().height));
        parent.add(section);
        parent.add(Box.createVerticalStrut(15));
    }

    private void setValues(AppSettings settings) {
        m_Theme.setSelectedItem(settings.theme());
        m_ExcludeFiller.setSelected(settings.excludeFiller());
        m_ExcludeRecaps.setSelected(settings.excludeRecaps());
        m_Weighting.setSelectedItem(settings.groupWeighting());
    }

    private void save() {
        if (m_Busy)
            return;
        AppSettings next = new AppSettings((AppSettings.Theme) m_Theme.getSelectedItem(),
                m_ExcludeFiller.isSelected(), m_ExcludeRecaps.isSelected(),
                (AppSettings.GroupWeighting) m_Weighting.getSelectedItem());
        setBusy(true, "Saving preferences...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                m_Repository.save(next);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    boolean appearanceChanged = next.theme() != m_Saved.theme();
                    m_Saved = next;
                    m_OnSaved.accept(next);
                    m_Status.setText(appearanceChanged
                            ? "Preferences saved. Restart the application to apply the appearance change."
                            : "Preferences saved.");
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

    private void clear(CacheMaintenanceService.CacheType type) {
        if (m_Busy)
            return;
        String description = type == CacheMaintenanceService.CacheType.ARTWORK
                ? "downloaded artwork" : "episode catalogs used for offline browsing and randomization";
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete all " + description + "?\nYour saved anime, settings, and selection history will not be changed.",
                "Clear cache", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION)
            return;

        setBusy(true, "Clearing " + description + "...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                m_Cache.clear(type);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    m_Status.setText("Cache cleared. Restart to reload artwork or catalogs when needed.");
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

    private void setBusy(boolean busy, String status) {
        m_Busy = busy;
        m_Save.setEnabled(!busy);
        m_Defaults.setEnabled(!busy);
        m_ClearArtwork.setEnabled(!busy);
        m_ClearEpisodes.setEnabled(!busy);
        m_Theme.setEnabled(!busy);
        m_Weighting.setEnabled(!busy);
        m_ExcludeFiller.setEnabled(!busy);
        m_ExcludeRecaps.setEnabled(!busy);
        if (status != null) {
            m_Status.setText(status);
            m_Status.setToolTipText(null);
        }
    }

    private void showError(Throwable cause) {
        String message = cause == null || cause.getMessage() == null ? "Unknown error" : cause.getMessage();
        m_Status.setText("Operation failed: " + message);
        m_Status.setToolTipText(message);
        JOptionPane.showMessageDialog(this, message, "Settings", JOptionPane.ERROR_MESSAGE);
    }
}
