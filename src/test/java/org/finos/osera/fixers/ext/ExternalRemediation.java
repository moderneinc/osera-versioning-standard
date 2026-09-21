package org.finos.osera.fixers.ext;

import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.GradleParser;
import org.openrewrite.groovy.tree.G;
import org.openrewrite.javascript.PackageJsonParser;
import org.openrewrite.json.JsonParser;
import org.openrewrite.json.tree.Json;
import org.openrewrite.kotlin.tree.K;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.toml.TomlParser;
import org.openrewrite.toml.tree.Toml;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.xml.tree.Xml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Makes a remediation tool that is neither a recipe nor on the JVM answer the same question as one
 * that is. The sources under test are printed into a directory, the tool is turned loose on that
 * directory, and whatever text it leaves behind is parsed back into the trees the TCK compares.
 */
public final class ExternalRemediation extends ScanningRecipe<ExternalRemediation.Sources> {

    /** Rewrites a directory of build files in place. */
    public interface Tool {
        String name();

        void run(Path directory, List<Path> files);
    }

    private final Tool tool;

    public ExternalRemediation(Tool tool) {
        this.tool = tool;
    }

    @Override
    public String getDisplayName() {
        return "Remediate with " + tool.name();
    }

    @Override
    public String getDescription() {
        return "Runs " + tool.name() + " over the build files under test and reads the result back.";
    }

    @Override
    public Sources getInitialValue(ExecutionContext ctx) {
        return new Sources(tool);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Sources sources) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof SourceFile source) {
                    sources.before.put(source.getSourcePath(), source);
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Sources sources) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile source)) {
                    return tree;
                }
                SourceFile remediated = sources.remediated(ctx).get(source.getSourcePath());
                return remediated == null ? source : remediated;
            }
        };
    }

    /** The sources under test, and the one run of the tool over them that a cycle is allowed. */
    public static final class Sources {
        private final Tool tool;
        private final Map<Path, SourceFile> before = new LinkedHashMap<>();
        private @Nullable Map<Path, SourceFile> after;

        private Sources(Tool tool) {
            this.tool = tool;
        }

        /** Only the files the tool actually changed, as trees. */
        Map<Path, SourceFile> remediated(ExecutionContext ctx) {
            if (after == null) {
                after = parse(run(), ctx);
            }
            return after;
        }

        private Map<Path, String> run() {
            Path work = createTemporaryDirectory();
            // The sources sit in their own subdirectory so that whatever a tool needs alongside
            // them — its script, its cache — is not something the tool then tries to remediate.
            Path directory = work.resolve("sources");
            try {
                for (Map.Entry<Path, SourceFile> source : before.entrySet()) {
                    Path file = directory.resolve(source.getKey());
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, source.getValue().printAll());
                }
                // A build tool reads a project, not a list of files, so it sees the shape it would
                // in a checkout. Sorted so that a parent pom precedes the modules under it.
                tool.run(directory, before.keySet().stream().sorted(Comparator.comparing(Path::toString)).toList());

                Map<Path, String> text = new LinkedHashMap<>();
                for (Path source : before.keySet()) {
                    text.put(source, Files.readString(directory.resolve(source)));
                }
                return text;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                deleteRecursively(work);
            }
        }

        /**
         * The tool hands back text, and the TCK compares trees, so the text has to become one
         * again. Every file of a kind goes through its parser together even when only one of them
         * changed, because a child pom that cannot see its parent resolves to nothing.
         */
        private Map<Path, SourceFile> parse(Map<Path, String> text, ExecutionContext ctx) {
            Map<Parser, List<Parser.Input>> byParser = new LinkedHashMap<>();
            before.forEach((path, source) ->
              // The path decides which Gradle DSL a build file is parsed as, so it has to survive.
              byParser.computeIfAbsent(parserFor(source), parser -> new ArrayList<>())
                .add(Parser.Input.fromString(path, text.get(path))));

            Map<Path, SourceFile> remediated = new LinkedHashMap<>();
            byParser.forEach((parser, inputs) -> parser.parseInputs(inputs, null, ctx).forEach(parsed -> {
                SourceFile original = before.get(parsed.getSourcePath());
                if (original != null && !parsed.printAll().equals(original.printAll())) {
                    // An id is what a run tracks a source file by, so a fresh one reads as a
                    // different file entirely and the change goes nowhere.
                    remediated.put(parsed.getSourcePath(), parsed.withId(original.getId()));
                }
            }));
            return remediated;
        }
    }

    private static Parser parserFor(SourceFile source) {
        return switch (source) {
            // A pom without its resolution result is not something the Maven assertions accept.
            case Xml.Document ignored when source.getSourcePath().endsWith("pom.xml") -> MavenParser.builder().build();
            case Xml.Document ignored -> XmlParser.builder().build();
            // SPIKE: package.json needs the marker-attaching parser, not the plain JSON one.
            case Json.Document ignored when source.getSourcePath().endsWith("package.json") ->
              PackageJsonParser.builder().build();
            case Json.Document ignored -> JsonParser.builder().build();
            case G.CompilationUnit ignored -> GradleParser.builder().build();
            case K.CompilationUnit ignored -> GradleParser.builder().build();
            case Properties.File ignored -> PropertiesParser.builder().build();
            case Toml.Document ignored -> TomlParser.builder().build();
            default -> throw new IllegalStateException("No parser for " + source.getClass().getSimpleName() +
                                                       "; add one when the TCK grows a source type");
        };
    }

    private static Path createTemporaryDirectory() {
        try {
            return Files.createTempDirectory("osera-remediation");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void deleteRecursively(Path directory) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
