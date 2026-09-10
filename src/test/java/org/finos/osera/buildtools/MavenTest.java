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
     * The module that uses the dependency is not the module that decides its version. Overriding
     * the property in the child as well is redundant, but it does not leave the build vulnerable.
     */
    @EachRemediator
    void multiModule(Remediator remediator) {
        rewriteRun(
          Remediator.asSpec(remediator, versioning),
          pomXml(
            //language=xml
            """
              <project>
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
                """,
              //language=xml
              """
                <project>
                    <parent>
                        <groupId>org.finos.osera</groupId>
                        <artifactId>osera-parent</artifactId>
                        <version>1.0-SNAPSHOT</version>
                    </parent>
                    <artifactId>osera-child</artifactId>
                    <properties>
                        <log4j.version>PATCH_ME</log4j.version>
                    </properties>
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
}
