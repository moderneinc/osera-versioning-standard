package org.finos.osera.npm;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockResponseBody;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okio.BufferedSink;

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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * A workspace cached by a previous run resolves against a port nothing is listening on any
     * more, and {@code DependencyWorkspace} keys its cache on the manifest alone, so it cannot
     * tell. Clearing once per JVM is the cheapest correct answer.
     */
    static {
        Path workspaces = Path.of(System.getProperty("java.io.tmpdir"), "openrewrite-js-workspaces");
        if (Files.isDirectory(workspaces)) {
            try (Stream<Path> paths = Files.walk(workspaces)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // A workspace another JVM is mid-install in; it owns its own cleanup.
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private final Map<String, Set<String>> versions = new ConcurrentHashMap<>();
    private final MockWebServer server = new MockWebServer();
    private final HttpClient upstream = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build();
    private final Path npmrc;

    public MockNpmRegistry() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return MockNpmRegistry.this.dispatch(URI.create(request.getTarget()).getPath());
            }
        });
        try {
            server.start();
            // Child processes (npm, and the npx that starts the recipe's RPC server) read npm
            // config from the file named by npm_config_userconfig, which the build points here.
            // The path is fixed because a child's environment cannot be changed after the JVM
            // starts; the content is written once a port exists.
            String configured = System.getProperty("osera.npmrc");
            npmrc = configured != null ? Path.of(configured) : Files.createTempFile("osera", ".npmrc");
            Files.createDirectories(npmrc.toAbsolutePath().getParent());
            Files.writeString(npmrc, "registry=" + url() + "\n");
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

    /** The npm config file the build has pointed child processes at. */
    public Path npmrc() {
        return npmrc;
    }

    /** Adds a version. Takes effect on the next request; the port does not move. */
    public void publish(String name, String version) {
        versions.computeIfAbsent(name, n -> ConcurrentHashMap.newKeySet()).add(version);
    }

    public Set<String> published(String name) {
        return Set.copyOf(versions.getOrDefault(name, Set.of()));
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
                  new MockResponse.Builder().body(new Bytes(Tarball.of(name, version))).build() :
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

    private String versionDocument(String name, String version) {
        byte[] tarball = Tarball.of(name, version);
        return """
          {
            "name": "%s",
            "version": "%s",
            "dist": {
              "tarball": "%s%s/-/%s-%s.tgz",
              "shasum": "%s",
              "integrity": "sha512-%s"
            }
          }
          """.formatted(name, version, url(), name, name, version,
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
              .body(new Bytes(body))
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
          .body(new Bytes(body.getBytes(UTF_8)))
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

    private record Bytes(byte[] content) implements MockResponseBody {
        @Override
        public long getContentLength() {
            return content.length;
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            sink.write(content);
        }
    }
}
