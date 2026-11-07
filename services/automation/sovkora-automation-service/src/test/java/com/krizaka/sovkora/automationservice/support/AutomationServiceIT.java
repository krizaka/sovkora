package com.krizaka.sovkora.automationservice.support;

import com.krizaka.sovkora.test.architecture.SqlBoundaryRules;
import com.krizaka.test.container.ServiceRoles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * A real PostgreSQL with this context's own bootstrap ({@code 50-automation.sql}: role and
 * database, run as the superuser exactly as {@code sovkora start} does), a real master keyring
 * file, and the service pointed at both under its own role, which applies {@code db/migration} at
 * startup as it does everywhere (krizaka-internal#2255). One container per JVM; nothing is mocked
 * below the HTTP port.
 */
public abstract class AutomationServiceIT {

  /** The shared HS256 secret the tests sign session tokens with. */
  public static final String JWT_SECRET = "automation-it-identity-jwt-secret-256bit!!";

  /** The Studio service, as far as the automation service can tell. */
  public static final StudioStub STUDIO = new StudioStub();

  private static final String ROLE = "orazaka_automation";
  private static final String PASSWORD = "orazaka_automation_pass";
  private static final String DATABASE = "orazaka_automation_db";
  private static final Path KEYRING = keyring();

  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("bootstrap")
          .withUsername("postgres")
          .withPassword("postgres")
          .withCopyFileToContainer(
              MountableFile.forHostPath(
                  SqlBoundaryRules.locateInitDb(Path.of(System.getProperty("user.dir")))
                      .resolve("50-automation.sql")),
              "/docker-entrypoint-initdb.d/50-automation.sql");

  static {
    if (System.getProperty("api.version") == null && System.getenv("DOCKER_API_VERSION") == null) {
      System.setProperty("api.version", "1.43");
    }
    POSTGRES.start();
    ServiceRoles.assignPassword(POSTGRES, ROLE, PASSWORD);
  }

  /**
   * A real master keyring file (mode 0600), as the asset store reads it: triggers seal their
   * secrets under it.
   */
  private static Path keyring() {
    try {
      byte[] key = new byte[32];
      new SecureRandom().nextBytes(key);
      Path file =
          Files.createTempFile(
              "automation-it-master",
              ".key",
              PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.writeString(
          file,
          "# sovkora master keyring v1\nactive = k1\nk1 = "
              + Base64.getEncoder().encodeToString(key)
              + "\n");
      file.toFile().deleteOnExit();
      return file;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Points the service at the container and keeps the broker out of the picture.
   *
   * @param registry the dynamic properties
   */
  @DynamicPropertySource
  static void wiring(DynamicPropertyRegistry registry) {
    registry.add(
        "orazaka.automation-service.datasource.url",
        () ->
            "jdbc:postgresql://%s:%d/%s"
                .formatted(POSTGRES.getHost(), POSTGRES.getMappedPort(5432), DATABASE));
    registry.add("orazaka.automation-service.datasource.username", () -> ROLE);
    registry.add("orazaka.automation-service.datasource.password", () -> PASSWORD);
    // One connection, as on dev: a transaction held across a remote call would deadlock these
    // tests.
    registry.add("orazaka.automation-service.datasource.max-pool-size", () -> "1");
    registry.add("orazaka.automation-service.studio.base-url", STUDIO::baseUrl);
    registry.add("orazaka.automation-service.scheduler.enabled", () -> "false");
    registry.add("orazaka.assets.encryption.master-key-file", KEYRING::toString);
    registry.add("krizaka.security.jwt.secret", () -> JWT_SECRET);
    // No broker, as in CI: the listeners do not start and a connection attempt fails fast and
    // quietly.
    registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    registry.add("spring.rabbitmq.host", () -> "127.0.0.1");
    registry.add("spring.rabbitmq.port", () -> "1");
    registry.add("management.tracing.sampling.probability", () -> "0.0");
  }
}
