package org.finos.osera.npm;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.Buffer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An npm registry that holds a producer's versions and proxies everything else to npmjs.
 *
 * <p>npm has no overlay: per-package {@code .npmrc} routing is ignored and this scheme has no scope
 * to route on, so a consumer points at exactly one registry. That makes an enterprise proxy the only
 * realistic topology, and it is the one modelled here. It is also a practical necessity: repointing
 * npm at a registry that does not carry {@code @openrewrite/rewrite} stops the recipe's own Node
 * process from starting.
 */
public final class MockNpmRegistry {

    private static final String UPSTREAM = "https://registry.npmjs.org";

    private final Map<String, Set<String>> versions = new ConcurrentHashMap<>();
    // What each version depends on. A registry that cannot say so cannot serve a scenario where
    // the backpatch arrives underneath something else: the lock engine re-resolves, finds the
    // package orphaned, and refuses with RESOLUTION_REQUIRED.
    private final Map<String, Map<String, String>> dependencies = new ConcurrentHashMap<>();
    // A packument names every version, and building one rebuilt every tarball and hashed it twice.
    private final Map<String, byte[]> tarballs = new ConcurrentHashMap<>();
    private final MockWebServer server = new MockWebServer();
    private final HttpClient upstream = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();

    public MockNpmRegistry() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return MockNpmRegistry.this.dispatch(URI.create(request.getTarget()).getPath());
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The registry base URL, ending in a slash.
     *
     * <p>Addressed by literal IPv4 rather than by name. {@code localhost} resolves to {@code ::1}
     * first on this platform while the server listens on IPv4, and a client that prefers the IPv6
     * answer does not fail fast: Dependabot reports it as {@code PrivateSourceTimedOut}, which
     * reads like an unreachable registry rather than a name that resolved the wrong way.
     */
    public String url() {
        return server.url("/").toString().replace("://localhost:", "://127.0.0.1:");
    }

    /** Adds a version. Takes effect on the next request; the port does not move. */
    public void publish(String name, String version) {
        publish(name, version, Map.of());
    }

    /** As {@link #publish(String, String)}, for a version that depends on something. */
    public void publish(String name, String version, Map<String, String> dependsOn) {
        versions.computeIfAbsent(name, n -> ConcurrentHashMap.newKeySet()).add(version);
        dependencies.put(name + "@" + version, dependsOn);
    }

    public Set<String> published(String name) {
        return Set.copyOf(versions.getOrDefault(name, Set.of()));
    }

    /**
     * Points the npm processes that measure real resolution at this registry, by writing the file
     * named by {@code npm_config_userconfig}.
     *
     * <p>Each of those writes its own project {@code .npmrc} naming this registry too, so this
     * file's job is only to neutralise whatever the developer has in {@code ~/.npmrc}. It is a
     * single shared path, so only the registry a run measures against may write it.
     */
    public void asDefaultRegistry() {
        String configured = System.getProperty("osera.npmrc");
        if (configured == null) {
            return;
        }
        try {
            Path npmrc = Path.of(configured);
            Files.createDirectories(npmrc.toAbsolutePath().getParent());
            Files.writeString(npmrc, "registry=" + url() + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every package this registry holds versions for, as opposed to proxies. */
    public Set<String> packages() {
        return Set.copyOf(versions.keySet());
    }

    private MockResponse dispatch(String path) {
        String withoutSlash = path.startsWith("/") ? path.substring(1) : path;

        for (String name : versions.keySet()) {
            if (withoutSlash.equals(name)) {
                return json(packument(name));
            }
            String tarballPrefix = name + "/-/" + name + "-";
            if (withoutSlash.startsWith(tarballPrefix) && withoutSlash.endsWith(".tgz")) {
                String version = withoutSlash.substring(tarballPrefix.length(), withoutSlash.length() - 4);
                return held(name, version) ?
                  new MockResponse.Builder().body(new Buffer().write(tarball(name, version))).build() :
                  new MockResponse.Builder().code(404).build();
            }
            if (withoutSlash.startsWith(name + "/")) {
                String version = withoutSlash.substring(name.length() + 1);
                if (held(name, version)) {
                    return json(versionDocument(name, version));
                }
            }
        }
        return proxy(path);
    }

    private boolean held(String name, String version) {
        return versions.getOrDefault(name, Set.of()).contains(version);
    }

    private String packument(String name) {
        String documents = sorted(name).stream()
          .map(version -> "\"%s\": %s".formatted(version, versionDocument(name, version)))
          .collect(Collectors.joining(",\n"));
        return """
          {
            "name": "%s",
            "dist-tags": { "latest": "%s" },
            "versions": { %s }
          }
          """.formatted(name, latest(name), documents);
    }

    /**
     * npm never moves {@code latest} onto a prerelease while a release exists, and a backpatch must
     * not become what {@code npm install <pkg>} hands out.
     */
    private String latest(String name) {
        List<String> all = sorted(name);
        List<String> releases = all.stream().filter(version -> !version.contains("-")).toList();
        return releases.isEmpty() ? all.getLast() : releases.getLast();
    }

    /** Publication order is not version order, and a packument reader expects the latter. */
    private List<String> sorted(String name) {
        return versions.getOrDefault(name, Set.of()).stream().sorted(SemverOrder.INSTANCE).toList();
    }

    private byte[] tarball(String name, String version) {
        return tarballs.computeIfAbsent(name + "@" + version,
          key -> Tarball.of(name, version, dependencies.getOrDefault(key, Map.of())));
    }

    private String versionDocument(String name, String version) {
        byte[] tarball = tarball(name, version);
        return """
          {
            "name": "%s",
            "version": "%s",
            "dependencies": %s,
            "dist": {
              "tarball": "%s%s/-/%s-%s.tgz",
              "shasum": "%s",
              "integrity": "sha512-%s"
            }
          }
          """.formatted(name, version,
          Tarball.json(dependencies.getOrDefault(name + "@" + version, Map.of())),
          url(), name, name, version,
          hex(digest("SHA-1", tarball)),
          Base64.getEncoder().encodeToString(digest("SHA-512", tarball)));
    }

    private MockResponse proxy(String path) {
        try {
            HttpResponse<byte[]> response = upstream.send(
              HttpRequest.newBuilder(URI.create(UPSTREAM + path)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
            String contentType = response.headers().firstValue("content-type")
              .orElse("application/octet-stream");
            byte[] body = response.body();
            if (contentType.contains("json")) {
                // Upstream advertises its own tarball URLs; a client following one would leave the
                // proxy and, for a package this registry overlays, see the unpatched view.
                body = new String(body, UTF_8)
                  .replace(UPSTREAM, url().substring(0, url().length() - 1))
                  .getBytes(UTF_8);
            }
            return new MockResponse.Builder()
              .code(response.statusCode())
              .setHeader("Content-Type", contentType)
              .body(new Buffer().write(body))
              .build();
        } catch (IOException e) {
            return new MockResponse.Builder().code(502).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new MockResponse.Builder().code(502).build();
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder()
          .setHeader("Content-Type", "application/json")
          .body(new Buffer().write(body.getBytes(UTF_8)))
          .build();
    }

    private static byte[] digest(String algorithm, byte[] content) {
        try {
            return MessageDigest.getInstance(algorithm).digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }
}
