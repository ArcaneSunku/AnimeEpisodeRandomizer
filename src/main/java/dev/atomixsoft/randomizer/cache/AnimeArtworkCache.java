package dev.atomixsoft.randomizer.cache;

import dev.atomixsoft.randomizer.model.Anime;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AnimeArtworkCache implements AutoCloseable {

    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private static final int COVER_WIDTH = 116;
    private static final int COVER_HEIGHT = 166;

    private final Path m_Directory;
    private final HttpClient m_Client;
    private final ExecutorService m_Executor = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "anime-artwork");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, CompletableFuture<ImageIcon>> m_Images = new ConcurrentHashMap<>();

    public AnimeArtworkCache(Path directory) {
        m_Directory = directory.toAbsolutePath().normalize();
        m_Client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public CompletableFuture<ImageIcon> load(Anime anime) {
        URI uri = imageUri(anime);
        if (uri == null)
            return CompletableFuture.completedFuture(null);

        String key = anime.malId() + ":" + uri;
        return m_Images.computeIfAbsent(key, ignored -> CompletableFuture.supplyAsync(() -> loadImage(anime, uri), m_Executor));
    }

    public ImageIcon getIfReady(Anime anime) {
        URI uri = imageUri(anime);
        if (uri == null)
            return null;

        CompletableFuture<ImageIcon> future = m_Images.get(anime.malId() + ":" + uri);
        return future != null && future.isDone() ? future.getNow(null) : null;
    }

    private ImageIcon loadImage(Anime anime, URI uri) {
        Path file = m_Directory.resolve(anime.malId() + "-" + fingerprint(uri.toString()) + ".img");

        try {
            if (Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES) {
                ImageIcon cached = decode(Files.readAllBytes(file));
                if (cached != null)
                    return cached;
            }

            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                    .header("Accept", "image/jpeg, image/png").GET().build();

            HttpResponse<InputStream> response = m_Client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            try (InputStream stream = response.body()) {
                if (response.statusCode() != 200 || response.headers().firstValueAsLong("Content-Length").orElse(0) > MAX_BYTES)
                    return null;

                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);

                if (bytes.length > MAX_BYTES)
                    return null;

                ImageIcon icon = decode(bytes);

                if (icon == null)
                    return null;

                try {
                    save(file, bytes);
                } catch (IOException ignored) {
                    // Artwork can still be displayed if the cache is not writable.
                }

                return icon;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException | IllegalArgumentException exception) {
            // Missing artwork must never prevent the library from being used.
        }

        return null;
    }

    private ImageIcon decode(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null)
                return null;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext())
                return null;

            ImageReader reader = readers.next();

            try {
                reader.setInput(input, true, true);

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);

                if (width < 1 || height < 1 || (long) width * height > 12_000_000)
                    return null;

                BufferedImage source = reader.read(0);
                BufferedImage cover = new BufferedImage(COVER_WIDTH, COVER_HEIGHT, BufferedImage.TYPE_INT_ARGB);

                double scale = Math.min((double) COVER_WIDTH / width, (double) COVER_HEIGHT / height);
                int scaledWidth = Math.max(1, (int) Math.round(width * scale));
                int scaledHeight = Math.max(1, (int) Math.round(height * scale));

                Graphics2D graphics = cover.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, (COVER_WIDTH - scaledWidth) / 2, (COVER_HEIGHT - scaledHeight) / 2, scaledWidth, scaledHeight, null);
                } finally {
                    graphics.dispose();
                }

                return new ImageIcon(cover);
            } finally {
                reader.dispose();
            }
        }
    }

    private void save(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(m_Directory);
        Path temporary = Files.createTempFile(m_Directory, "cover-", ".tmp");

        try {
            Files.write(temporary, bytes);

            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private URI imageUri(Anime anime) {
        if (anime == null || anime.imageUrl() == null || anime.imageUrl().isBlank())
            return null;

        try {
            URI uri = URI.create(anime.imageUrl());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getPort() != -1 ||
                    uri.getUserInfo() != null || !"cdn.myanimelist.net".equalsIgnoreCase(uri.getHost()))
                return null;

            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @Override
    public void close() {
        m_Executor.shutdownNow();
    }

}