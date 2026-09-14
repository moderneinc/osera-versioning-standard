package org.finos.osera.buildtools;

import org.finos.osera.fixers.EachRemediator;
import org.finos.osera.fixers.Remediator;
import org.finos.osera.providers.ProviderVersioning;
import org.openrewrite.maven.tree.GroupArtifactVersion;

import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.test.SourceSpecs.dir;

/**
 * The ways a Maven build can arrive at a dependency's version, each of which a remediation tool has
 * to recognize before it can move that dependency to a producer's patch.
 */
public record MavenTest(ProviderVersioning versioning) implements MavenBuild {

    @EachRemediator
    void direct(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>2.14.1</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>PATCH_ME</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    @EachRemediator
    void transitive(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-log4j2</artifactId>
                          <version>2.5.7</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-core</artifactId>
                              <version>PATCH_ME</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework.boot</groupId>
                          <artifactId>spring-boot-starter-log4j2</artifactId>
                          <version>2.5.7</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    /**
     * The version reaches the dependency through a property of the same pom, so the property is
     * what has to move.
     */
    @EachRemediator
    void versionProperty(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <log4j.version>2.14.1</log4j.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>${log4j.version}</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <log4j.version>PATCH_ME</log4j.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>${log4j.version}</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    /** The dependency is declared version-less and pinned by the pom's own dependency management. */
    @EachRemediator
    void managed(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-core</artifactId>
                              <version>2.14.1</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-core</artifactId>
                              <version>PATCH_ME</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    /**
     * An imported BOM holds the version, and it is not the producer's to patch, so the only place
     * left to say otherwise is the consumer's own pom.
     */
    @EachRemediator
    void importedBom(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-bom</artifactId>
                              <version>2.14.1</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-bom</artifactId>
                              <version>2.14.1</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>org.apache.logging.log4j</groupId>
                          <artifactId>log4j-core</artifactId>
                          <version>PATCH_ME</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    /** A build plugin's own dependencies run in the same JVM as the build, and age the same way. */
    @EachRemediator
    void pluginDependency(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.2.5</version>
                              <dependencies>
                                  <dependency>
                                      <groupId>org.apache.logging.log4j</groupId>
                                      <artifactId>log4j-core</artifactId>
                                      <version>2.14.1</version>
                                  </dependency>
                              </dependencies>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <build>
                      <plugins>
                          <plugin>
                              <groupId>org.apache.maven.plugins</groupId>
                              <artifactId>maven-surefire-plugin</artifactId>
                              <version>3.2.5</version>
                              <dependencies>
                                  <dependency>
                                      <groupId>org.apache.logging.log4j</groupId>
                                      <artifactId>log4j-core</artifactId>
                                      <version>PATCH_ME</version>
                                  </dependency>
                              </dependencies>
                          </plugin>
                      </plugins>
                  </build>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          )
        );
    }

    /**
     * The module that uses the dependency is not the module that decides its version. Only the
     * parent's property moves: an override in the child would leave two places to maintain, and
     * would pin the child if the parent later moved again.
     */
    @EachRemediator
    void multiModule(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-parent</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>child</module>
                  </modules>
                  <properties>
                      <log4j.version>2.14.1</log4j.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-core</artifactId>
                              <version>${log4j.version}</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-parent</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <packaging>pom</packaging>
                  <modules>
                      <module>child</module>
                  </modules>
                  <properties>
                      <log4j.version>PATCH_ME</log4j.version>
                  </properties>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>org.apache.logging.log4j</groupId>
                              <artifactId>log4j-core</artifactId>
                              <version>${log4j.version}</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.apache.logging.log4j", "log4j-core", "2.14.1"))
          ),
          dir("child",
            pomXml(
              //language=xml
              """
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.finos.osera</groupId>
                        <artifactId>osera-parent</artifactId>
                        <version>1.0-SNAPSHOT</version>
                    </parent>
                    <artifactId>osera-child</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.logging.log4j</groupId>
                            <artifactId>log4j-core</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    /**
     * The Spring Boot parent manages snakeyaml's version through a property, so the idiomatic fix
     * is to override {@code snakeyaml.version} rather than to pin a coordinate. Log4j is managed
     * through an imported BOM instead, which no property override can reach.
     */
    @EachRemediator
    void springBootParent(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.5.7</version>
                  </parent>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.yaml</groupId>
                          <artifactId>snakeyaml</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <parent>
                      <groupId>org.springframework.boot</groupId>
                      <artifactId>spring-boot-starter-parent</artifactId>
                      <version>2.5.7</version>
                  </parent>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <properties>
                      <snakeyaml.version>PATCH_ME</snakeyaml.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>org.yaml</groupId>
                          <artifactId>snakeyaml</artifactId>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.yaml", "snakeyaml", "1.28"))
          )
        );
    }

    /**
     * Spring's OSGi-era versions end in {@code .RELEASE}, as Hibernate's and Netty's end in
     * {@code .Final}. CARE cannot append a numeric branch segment to one of those, so its alternate
     * format applies, and every tool downstream reads a version carrying two qualifiers.
     */
    @EachRemediator
    void qualifiedVersion(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework</groupId>
                          <artifactId>spring-beans</artifactId>
                          <version>5.2.19.RELEASE</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>org.springframework</groupId>
                          <artifactId>spring-beans</artifactId>
                          <version>PATCH_ME</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("org.springframework", "spring-beans", "5.2.19.RELEASE"))
          )
        );
    }

    /**
     * A second CVE against the same release, so the producer publishes twice against it. Which of
     * the two is newest is decided by the producer's sequence rather than by anything the base
     * version says, and the remediation has to land on the second.
     *
     * <p>Jackson keeps a fourth numeric segment for its own security releases — Central holds
     * {@code 2.12.6.1}, {@code 2.12.7.1}, {@code 2.13.2.1} and more — so a backpatch of
     * {@code 2.12.6} lands on the same numeric point the maintainer uses for the same purpose.
     * Dependabot, which is told a ceiling rather than a producer, takes the maintainer's
     * {@code 2.12.6.1} here rather than making no change as it does everywhere else.
     */
    @EachRemediator
    void secondPatch(Remediator remediator) {
        versioning.publish(new GroupArtifactVersion("com.fasterxml.jackson.core", "jackson-databind", "2.12.6"));
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>2.12.6</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            //language=xml
            """
              <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>org.finos.osera</groupId>
                  <artifactId>osera-test</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <dependencies>
                      <dependency>
                          <groupId>com.fasterxml.jackson.core</groupId>
                          <artifactId>jackson-databind</artifactId>
                          <version>PATCH_ME</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
            versioning.patch(new GroupArtifactVersion("com.fasterxml.jackson.core", "jackson-databind", "2.12.6"), 2)
          )
        );
    }
}
