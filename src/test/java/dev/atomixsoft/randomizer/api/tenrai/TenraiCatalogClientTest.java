package dev.atomixsoft.randomizer.api.tenrai;

import com.sun.net.httpserver.HttpServer;
import dev.atomixsoft.randomizer.model.AnimeEpisode;
import dev.atomixsoft.randomizer.model.Page;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public final class TenraiCatalogClientTest {

    @Test
    void parsesDocumentedEpisodeResponse() throws Exception {
        String response = """
                {
                  "pagination": {"last_visible_page": 1, "has_next_page": false},
                  "data": [
                    {"mal_id": 1, "title": "Asteroid Blues", "filler": false, "recap": false},
                    {"mal_id": 2, "title": "Stray Dog Strut", "filler": true, "recap": false}
                  ]
                }
                """;

        Page<AnimeEpisode> episodes = loadFixture(response);

        assertEquals(1, episodes.number());
        assertFalse(episodes.hasNextPage());
        assertEquals(2, episodes.items().size());
        assertEquals("Asteroid Blues", episodes.items().get(0).title());
        assertTrue(episodes.items().get(1).filler());
    }

    @Test
    void missingPaginationIsRejected() {
        String response = """
                {"pagination": {}, "data": [{"mal_id": 1, "title": "Episode", "filler": false, "recap": false}]}
                """;

        assertThrows(IOException.class, () -> loadFixture(response));
    }

    @Test
    void missingEpisodeClassificationIsRejected() {
        String response = """
                {"pagination": {"has_next_page": false}, "data": [{"mal_id": 1, "title": "Episode"}]}
                """;

        assertThrows(IOException.class, () -> loadFixture(response));
    }

    private Page<AnimeEpisode> loadFixture(String json) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/anime/1/episodes", exchange -> {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);

            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });

        server.start();

        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
            TenraiCatalogClient client = new TenraiCatalogClient(HttpClient.newHttpClient(), base);

            return client.getEpisodes(1, 1);
        } finally {
            server.stop(0);
        }
    }

}