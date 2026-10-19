package dev.atomixsoft.randomizer;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import dev.atomixsoft.randomizer.persistence.ApplicationPaths;
import dev.atomixsoft.randomizer.settings.AppSettings;
import dev.atomixsoft.randomizer.settings.JsonSettingsRepository;
import dev.atomixsoft.randomizer.ui.MainWindow;
import dev.atomixsoft.randomizer.ui.UITheme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.io.IOException;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No graphical display available. Verify your desktop session and display server.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            JsonSettingsRepository repository = new JsonSettingsRepository(
                    ApplicationPaths.dataDirectory().resolve("settings.json"));
            AppSettings settings;

            try {
                settings = repository.load();
            } catch (IOException exception) {
                int choice = JOptionPane.showConfirmDialog(null,
                        "Preferences could not be loaded.\n" + exception.getMessage()
                                + "\n\nStart with defaults? The existing file will only be replaced if you save preferences.",
                        "Preferences unavailable", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION)
                    return;
                settings = AppSettings.defaults();
            }

            boolean light = settings.theme() == AppSettings.Theme.LIGHT;
            if (light)
                FlatLightLaf.setup();
            else
                FlatDarkLaf.setup();

            UITheme.applyPalette(light);
            UITheme.configure();
            new MainWindow(repository, settings).setVisible(true);
        });
    }
}
