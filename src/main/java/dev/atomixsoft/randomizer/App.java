package dev.atomixsoft.randomizer;

import com.formdev.flatlaf.FlatDarkLaf;
import dev.atomixsoft.randomizer.ui.MainWindow;
import dev.atomixsoft.randomizer.ui.UITheme;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;

public final class App {

    private App() {

    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("No graphical display available. On Hyprland, verify that XWayland is running and DISPLAY is set.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            FlatDarkLaf.setup();
            UITheme.configure();

            new MainWindow().setVisible(true);
        });
    }

}