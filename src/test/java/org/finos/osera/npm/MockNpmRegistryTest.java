package org.finos.osera.npm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class MockNpmRegistryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode get(String url) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
          HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    @Test
    void servesAPackumentForWhatItHolds() throws Exception {
        MockNpmRegistry registry = new MockNpmRegistry();
        registry.publish("acme-logger", "1.4.0");
        registry.publish("acme-logger", "1.4.1");
        registry.publish("acme-logger", "1.4.2-osera-00001");
        registry.publish("acme-logger", "1.5.0");

        JsonNode packument = get(registry.url() + "acme-logger");

        assertThat(packument.get("versions").fieldNames()).toIterable()
          .containsExactly("1.4.0", "1.4.1", "1.4.2-osera-00001", "1.5.0");
        assertThat(packument.get("dist-tags").get("latest").asText())
          .as("latest never points at a prerelease")
          .isEqualTo("1.5.0");
    }

    @Test
    void servesTheVersionDocumentTheLockEngineAsksFor() throws Exception {
        MockNpmRegistry registry = new MockNpmRegistry();
        registry.publish("acme-logger", "1.4.1");

        JsonNode version = get(registry.url() + "acme-logger/1.4.1");

        assertThat(version.get("version").asText()).isEqualTo("1.4.1");
        assertThat(version.get("dist").get("tarball").asText()).startsWith(registry.url());
    }

    @Test
    void proxiesWhatItDoesNotHold() throws Exception {
        MockNpmRegistry registry = new MockNpmRegistry();

        // Any real package proves this; a small one keeps the test from pulling a ten-megabyte
        // packument to assert three things about its shape.
        JsonNode packument = get(registry.url() + "is-odd");

        assertThat(packument.get("name").asText()).isEqualTo("is-odd");
        assertThat(packument.get("versions").size()).isGreaterThan(0);
        assertThat(packument.toString())
          .as("upstream tarball URLs are rewritten so clients stay on the proxy")
          .doesNotContain("registry.npmjs.org");
    }

    @Test
    void whatItHoldsCanChangeWithoutRestarting() throws Exception {
        MockNpmRegistry registry = new MockNpmRegistry();
        registry.publish("acme-logger", "1.4.1");
        String url = registry.url();

        assertThat(get(url + "acme-logger").get("versions").size()).isEqualTo(1);

        registry.publish("acme-logger", "1.4.2-osera-00001");

        assertThat(get(url + "acme-logger").get("versions").size()).isEqualTo(2);
        assertThat(registry.url()).as("the port does not move").isEqualTo(url);
    }
}
