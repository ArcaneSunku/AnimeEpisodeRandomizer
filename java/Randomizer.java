import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Meant to intake a file full of numbers (based on anime episodes) and choose one
 * before showing the selection and removing it from the file.
 */

public class Randomizer extends JPanel implements ActionListener {

    private static final String Title = "Anime Randomizer";

    private final JFrame m_Frame;
    private final JTextArea m_Log;
    private final JFileChooser m_Chooser;
    private final JButton m_ChooseFile, m_ChooseEpisode;

    private File m_CurrentFile;
    private String m_CurrentFileName;
    private int m_SelectedEpisode;
    private List<Integer> m_EpisodeNumbers;

    public Randomizer() {
        super(new BorderLayout());
        m_Frame = new JFrame(Title);
        m_Chooser = new JFileChooser();
        m_Log = new JTextArea(5, 20);
        m_EpisodeNumbers = new ArrayList<>();

        m_Frame.setIconImage(createImageIcon("images/icon.gif").getImage());
        m_ChooseFile = new JButton("Choose File...", createImageIcon("images/Open16.gif"));
        m_ChooseEpisode = new JButton("Choose Episode...", createImageIcon("images/Choose16.gif"));
    }

    @Override
    public void actionPerformed(ActionEvent action) {
        if(action.getSource() == m_ChooseFile) {
            int returnVal = m_Chooser.showOpenDialog(this);

            if(returnVal == JFileChooser.APPROVE_OPTION) {
                m_CurrentFile = m_Chooser.getSelectedFile();

                if(m_CurrentFile != null) {
                    m_CurrentFileName = m_CurrentFile.getName();
                    List<Integer> episodeList = new ArrayList<>();
                    m_Log.append(String.format("Opening: %s.%n", m_CurrentFileName));
                    try {
                        Scanner scanner = new Scanner(m_CurrentFile);

                        int episodeCount = 0;
                        while(scanner.hasNextInt()) {
                            episodeList.add(scanner.nextInt());
                            episodeCount++;
                        }

                        if(m_EpisodeNumbers.size() != episodeCount) {
                            m_EpisodeNumbers = new ArrayList<>();
                            m_EpisodeNumbers.addAll(episodeList);
                        }
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                m_Log.append("Didn't open a file!\n");
            }
            m_Log.setCaretPosition(m_Log.getDocument().getLength());
        } else if(action.getSource() == m_ChooseEpisode) {
            if(m_CurrentFile == null && m_EpisodeNumbers.size() <= 0) {
                m_Log.append("There is no file to choose from!\n");
            } else {

                if(m_EpisodeNumbers.size() <= 0) {
                    m_Log.append("There are no more episodes!\n");
                } else {
                    int episodeIndex = ThreadLocalRandom.current().nextInt(0, m_EpisodeNumbers.size());
                    m_SelectedEpisode = m_EpisodeNumbers.get(episodeIndex);
                    m_Log.append(String.format("We chose episode: %d!%n", m_SelectedEpisode));

                    for (int i = 0; i < m_EpisodeNumbers.size(); i++) {
                        if (m_EpisodeNumbers.get(i) == m_SelectedEpisode) {
                            m_EpisodeNumbers.remove(i);
                            break;
                        }
                    }
                }

                try {
                    FileWriter writer = new FileWriter(m_CurrentFile, false);

                    if(m_EpisodeNumbers.size() > 0) {
                        for (Integer m_episodeNumber : m_EpisodeNumbers) {
                            writer.write(m_episodeNumber + " ");
                        }
                    } else {
                        writer.write("");
                    }

                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            m_Log.setCaretPosition(m_Log.getDocument().getLength());
        }
    }

    private void createAndShowRandomizer() {
        String appDir = System.getProperty("user.dir");
        m_Chooser.setCurrentDirectory(new File(appDir));
        JPanel buttonPanel = new JPanel();

        m_Log.setMargin(new Insets(5, 5, 5 ,5));
        m_Log.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(m_Log);

        m_ChooseFile.addActionListener(this);
        m_ChooseEpisode.addActionListener(this);

        buttonPanel.add(m_ChooseFile);
        buttonPanel.add(m_ChooseEpisode);

        add(buttonPanel, BorderLayout.PAGE_START);
        add(logScrollPane, BorderLayout.CENTER);

        m_Frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        m_Frame.add(this);
        m_Frame.pack();
        m_Frame.setLocationRelativeTo(null);
        m_Frame.setResizable(false);
        m_Frame.setVisible(true);
    }

    protected static ImageIcon createImageIcon(String path) {
        URL imgURL = Randomizer.class.getResource(path);
        if(imgURL != null) {
            return new ImageIcon(imgURL);
        } else {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }

    public static void main(String[] args) {
        Randomizer animeRandomizer = new Randomizer();

        SwingUtilities.invokeLater(() -> {
            UIManager.put("swing.boldMetal", Boolean.FALSE);
            animeRandomizer.createAndShowRandomizer();
        });
    }
}
