package dev.atomixsoft.randomizer.api.tenrai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atomixsoft.randomizer.api.AnimeCatalogClient;
import dev.atomixsoft.randomizer.model.Anime;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.Page;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TenraiCatalogClient implements AnimeCatalogClient {

    private static final Duration REQUEST_INTERVAL = Duration.ofMillis(1100);
    private static final Duration SEARCH_CACHE_LIFETIME = Duration.ofMinutes(10);
    private static final Duration EPISODE_CACHE_LIFETIME = Duration.ofHours(6);

    private record CacheEntry(JsonNode response, Instant expiresAt) {
    }

    private final HttpClient m_HttpClient;
    private final URI m_BaseUri;
    private final ObjectMapper m_Mapper = new ObjectMapper();

    private final Map<String, CacheEntry> m_Cache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > 96;
        }
    };

    private long m_NextRequestNanos;

    public TenraiCatalogClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12))
            .build(), URI.create("https://api.tenrai.org/v1"));
    }

    public TenraiCatalogClient(HttpClient httpClient, URI baseUri) {
        m_HttpClient = httpClient;
        m_BaseUri = baseUri;
    }

    @Override
    public Page<Anime> searchAnime(String query, int page) throws IOException, InterruptedException {
        if (query == null || query.isBlank())
            throw new IllegalArgumentException("Search query must not be blank.");

        if (page < 1)
            throw new IllegalArgumentException("Page number must be positive.");

        String encodedQuery = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);

        JsonNode response = fetch("/anime?q=" + encodedQuery + "&page=" + page + "&limit=12", SEARCH_CACHE_LIFETIME);
        List<Anime> results = new ArrayList<>();

        for (JsonNode item : data(response)) {
            int id = item.path("mal_id").asInt(0);
            if (id <= 0)
                continue;

            String title = value(item, "title_english");
            if (title.isBlank())
                title = value(item, "title");

            int count = item.path("episodes").asInt(0);
            String imageUrl = value(item.path("images").path("jpg"), "large_image_url");

            results.add(new Anime(
                    id,
                    title.isBlank() ? "Untitled anime" : title,
                    value(item, "type"),
                    count > 0 ? count : null,
                    imageUrl));
        }

        return new Page<>(results, page, hasNextPage(response));
    }

    @Override
    public Page<AnimeEpisode> getEpisodes(int animeId, int page) throws IOException, InterruptedException {
        if (animeId <= 0 || page < 1)
            throw new IllegalArgumentException("Anime ID and page number must be positive.");

        JsonNode response = fetch("/anime/" + animeId + "/episodes?page=" + page, EPISODE_CACHE_LIFETIME);

        List<AnimeEpisode> episodes = new ArrayList<>();
        for (JsonNode item : data(response)) {
            int number = item.path("mal_id").asInt(0);

            if (number <= 0)
                continue;

            String title = value(item, "title");
            episodes.add(new AnimeEpisode(
                    number,
                    title.isBlank() ? "Untitled episode" : title,
                    item.path("filler").asBoolean(false),
                    item.path("recap").asBoolean(false)
            ));
        }

        return new Page<>(episodes, page, hasNextPage(response));
    }

    private synchronized JsonNode fetch(String path, Duration cacheLifetime) throws IOException, InterruptedException {
        CacheEntry cached = m_Cache.get(path);

        if (cached != null && Instant.now().isBefore(cached.expiresAt()))
            return cached.response();

        throttle();

        HttpRequest request = HttpRequest.newBuilder(m_BaseUri.resolve(m_BaseUri.getPath() + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "AnimeEpisodeRandomizer")
                .GET()
                .build();

        HttpResponse<String> response = m_HttpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int status = response.statusCode();
        if (status == 429) {
            String retryAfter = response.headers().firstValue("Retry-After").orElse("a moment");
            throw new IOException("Tenrai rate limit reached. Try again after " + retryAfter + ".");
        }

        if (status == 404)
            throw new IOException("The requested anime or episode page was not found.");

        if (status != 200)
            throw new IOException("Tenrai request failed (HTTP " + status + ").");

        JsonNode json = m_Mapper.readTree(response.body());
        m_Cache.put(path, new CacheEntry(json, Instant.now().plus(cacheLifetime)));

        return json;
    }

    private void throttle() throws InterruptedException {
        long remaining = m_NextRequestNanos - System.nanoTime();
        if (remaining > 0)
            Thread.sleep((remaining + 999_999L) / 1_000_000L);

        m_NextRequestNanos = System.nanoTime() + REQUEST_INTERVAL.toNanos();
    }

    private JsonNode data(JsonNode response) throws IOException {
        JsonNode items = response.path("data");
        if (!items.isArray())
            throw new IOException("The anime catalog returned an unexpected response.");

        return items;
    }

    private boolean hasNextPage(JsonNode response) {
        return response.path("pagination").path("has_next_page").asBoolean(false);
    }

    private String value(JsonNode node, String name) {
        return node.path(name).asText("");
    }

}