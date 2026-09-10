package org.finos.osera;

import mockwebserver3.*;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import okio.BufferedSink;
import org.openrewrite.maven.tree.GroupArtifactVersion;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * A Maven 2 repository layout served over HTTPS with a self-signed certificate, standing in for
 * Maven Central or a producer's patch repository for whichever tool is under test.
 */
public final class MockMavenRepository implements AutoCloseable {

    public static final String TRUST_STORE_PASSWORD = "changeit";

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Set<GroupArtifactVersion> versions = new LinkedHashSet<>();
    private final HeldCertificate certificate;
    private final Path trustStore;
    private final Path certificateBundle;
    private final MockWebServer server = new MockWebServer();

    public MockMavenRepository() {
        certificate = new HeldCertificate.Builder()
          .commonName("Mock Maven repository")
          .addSubjectAlternativeName("localhost")
          .build();
        trustStore = trustStore(certificate.certificate());
        certificateBundle = write("mock-maven-repository", ".pem", certificate.certificatePem() + trustedCertificatesPem());
        server.useHttps(new HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory());
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                byte[] content = files.get(URI.create(request.getTarget()).getPath().substring(1));
                if (content == null) {
                    return new MockResponse.Builder().code(404).build();
                }
                return "HEAD".equals(request.getMethod()) ?
                  new MockResponse.Builder().setHeader("Content-Length", content.length).build() :
                  new MockResponse.Builder().body(new Bytes(content)).build();
            }
        });
        try {
            server.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        // OpenRewrite's default HttpSender is HttpsURLConnection, which has no per-client trust hook.
        HttpsURLConnection.setDefaultSSLSocketFactory(new HandshakeCertificates.Builder()
          .addPlatformTrustedCertificates()
          .addTrustedCertificate(certificate.certificate())
          .build()
          .sslSocketFactory());
    }

    /**
     * The base URL a build tool would declare, ending in a slash.
     */
    public String url() {
        return server.url("/").toString();
    }

    /**
     * Clients must trust this before they can see anything else.
     */
    public X509Certificate certificate() {
        return certificate.certificate();
    }

    /** Everything published so far, in order; what a producer would advertise to remediation tools. */
    public Set<GroupArtifactVersion> published() {
        return Set.copyOf(versions);
    }

    /** The JDK's own truststore plus {@link #certificate()}, for JVMs a test spawns; password {@link #TRUST_STORE_PASSWORD}. */
    public Path trustStore() {
        return trustStore;
    }

    /**
     * {@link #trustStore()} in PEM, for the tools that are not JVMs. It has to be the whole bundle,
     * not just this server's certificate: {@code SSL_CERT_FILE} replaces Ruby's trusted set rather
     * than adding to it, so a lone certificate would leave Maven Central unverifiable.
     */
    public Path certificateBundle() {
        return certificateBundle;
    }

    private static String trustedCertificatesPem() {
        StringBuilder pem = new StringBuilder();
        try {
            KeyStore store = cacerts();
            for (String alias : Collections.list(store.aliases())) {
                if (store.getCertificate(alias) instanceof X509Certificate trusted) {
                    pem.append("-----BEGIN CERTIFICATE-----\n")
                      .append(Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(trusted.getEncoded()))
                      .append("\n-----END CERTIFICATE-----\n");
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        return pem.toString();
    }

    private static Path write(String prefix, String suffix, String content) {
        try {
            Path path = Files.createTempFile(prefix, suffix);
            Files.writeString(path, content);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static KeyStore cacerts() throws GeneralSecurityException {
        try {
            return KeyStore.getInstance(Path.of(System.getProperty("java.home"), "lib", "security", "cacerts").toFile(),
              TRUST_STORE_PASSWORD.toCharArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path trustStore(X509Certificate certificate) {
        try {
            KeyStore store = cacerts();
            store.setCertificateEntry("mock-maven-repository", certificate);
            Path path = Files.createTempFile("mock-maven-repository", ".p12");
            try (OutputStream out = Files.newOutputStream(path)) {
                store.store(out, TRUST_STORE_PASSWORD.toCharArray());
            }
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Publishes a version with a minimal pom and jar, and lists it in the artifact's metadata.
     */
    public void publish(GroupArtifactVersion gav) {
        String groupId = requireNonNull(gav.getGroupId());
        String artifactId = gav.getArtifactId();
        String version = requireNonNull(gav.getVersion());
        versions.add(gav);
        String dir = groupId.replace('.', '/') + "/" + artifactId;
        put("%s/%s/%s-%s.pom".formatted(dir, version, artifactId, version),
          //language=xml
          """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>%s</version>
            </project>
            """.formatted(groupId, artifactId, version));
        // Gradle refuses a module whose artifact is missing, so a patch has to ship something.
        put("%s/%s/%s-%s.jar".formatted(dir, version, artifactId, version), emptyJar());
        put("%s/maven-metadata.xml".formatted(dir),
          //language=xml
          """
            <metadata>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <versioning>
                <latest>%s</latest>
                <release>%s</release>
                <versions>
                  %s
                </versions>
                <lastUpdated>%s</lastUpdated>
              </versioning>
            </metadata>
            """.formatted(groupId, artifactId, version, version,
            //language=none
            versions.stream()
              .filter(v -> v.asGroupArtifact().equals(gav.asGroupArtifact()))
              .map(v -> "<version>%s</version>".formatted(v.getVersion()))
              .collect(joining("\n")),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now())));
    }

    private static byte[] emptyJar() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes, manifest)) {
            jar.putNextEntry(new JarEntry("META-INF/"));
            jar.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * @param path Repository-relative, e.g. {@code org/apache/logging/log4j/log4j-core/maven-metadata.xml}.
     */
    public void put(String path, byte[] content) {
        files.put(path, content);
    }

    public void put(String path, String content) {
        put(path, content.getBytes(UTF_8));
    }

    @Override
    public void close() {
        server.close();
        try {
            Files.deleteIfExists(trustStore);
            Files.deleteIfExists(certificateBundle);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
