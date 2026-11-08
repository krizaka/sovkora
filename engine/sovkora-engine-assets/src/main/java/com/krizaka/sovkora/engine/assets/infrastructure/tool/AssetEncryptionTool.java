package com.krizaka.sovkora.engine.assets.infrastructure.tool;

import com.krizaka.sovkora.crypto.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.engine.assets.application.service.EncryptedAssetService;
import com.krizaka.sovkora.engine.assets.domain.model.EnvelopeHeader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The three operations an operator runs by hand: {@code keygen}, {@code migrate}, {@code verify}.
 *
 * <p>A {@code main} rather than an endpoint, and deliberately. Creating the master key and
 * converting the store are things done once, by a person, with the services stopped — an HTTP
 * surface for either would be an HTTP surface for "re-encrypt everything" and "make me a key".
 *
 * <pre>
 * java -cp … AssetEncryptionTool keygen  &lt;keyfile&gt; &lt;keyId&gt;
 * java -cp … AssetEncryptionTool migrate &lt;keyfile&gt; &lt;uploadRoot&gt;
 * java -cp … AssetEncryptionTool verify  &lt;keyfile&gt; &lt;uploadRoot&gt;
 * </pre>
 *
 * <p><b>The migration is resumable because it holds no state.</b> A file is converted if and only
 * if it opens with {@link EnvelopeHeader#MAGIC}, so "where did it stop" is a question the store
 * answers itself — there is no ledger to fall out of step with the data. Killed halfway, it is
 * re-run; each file is written to a sibling temp and moved into place, so no file is ever half
 * converted.
 *
 * <p><b>And verifiable</b>: {@code verify} opens every encrypted file and checks it decrypts to the
 * length its header claims, which is the difference between "the migration ran" and "the migration
 * worked".
 */
public final class AssetEncryptionTool {

  private static final Logger log = LoggerFactory.getLogger(AssetEncryptionTool.class);

  private AssetEncryptionTool() {}

  /**
   * Runs the tool and exits with its status; the exit code is what an operator's script reads.
   *
   * @param args the subcommand and its two arguments
   * @throws IOException if the store cannot be walked
   */
  public static void main(String[] args) throws IOException {
    int status = run(args);
    if (status != 0) {
      System.exit(status);
    }
  }

  /**
   * Runs one subcommand.
   *
   * @param args the subcommand and its two arguments
   * @return the exit status: {@code 0} done, {@code 1} at least one file failed, {@code 2} a usage
   *     error
   * @throws IOException if the store cannot be walked
   */
  static int run(String[] args) throws IOException {
    if (args.length < 3) {
      System.err.println(
          "usage: AssetEncryptionTool <keygen|migrate|verify> <keyfile> <keyId|uploadRoot>");
      return 2;
    }
    Path keyFile = Path.of(args[1]);
    Command command = COMMANDS.get(args[0]);
    if (command == null) {
      System.err.println("unknown subcommand: " + args[0]);
      return 2;
    }
    return switch (command) {
      case KEYGEN -> keygen(keyFile, args[2]);
      case MIGRATE -> report(migrate(keyFile, Path.of(args[2])));
      case VERIFY -> report(verify(keyFile, Path.of(args[2])));
    };
  }

  private static int keygen(Path keyFile, String keyId) throws IOException {
    FileMasterKeyProvider.addKey(keyFile, keyId);
    System.out.println(
        "Wrote master key '" + keyId + "' to " + keyFile + " (mode 0600) and made it active.");
    System.out.println(
        "Every retired key must stay in this file for as long as one asset still names it.");
    return 0;
  }

  private static final String OP_MIGRATE = "migrate";
  private static final String OP_VERIFY = "verify";

  private enum Command {
    KEYGEN,
    MIGRATE,
    VERIFY
  }

  private static final Map<String, Command> COMMANDS =
      Map.of("keygen", Command.KEYGEN, OP_MIGRATE, Command.MIGRATE, OP_VERIFY, Command.VERIFY);

  /**
   * Converts every plaintext file under {@code uploadRoot}, skipping what is already done.
   *
   * @param keyFile the keyring
   * @param uploadRoot the store
   * @return what it found and what it changed
   * @throws IOException if the store cannot be walked
   */
  public static Outcome migrate(Path keyFile, Path uploadRoot) throws IOException {
    EncryptedAssetService service =
        new EncryptedAssetService(
            new FileMasterKeyProvider(keyFile), EnvelopeHeader.DEFAULT_BLOCK_SIZE);
    int converted = 0;
    int alreadyDone = 0;
    List<String> failed = new ArrayList<>();
    for (Path file : files(uploadRoot)) {
      try {
        if (service.encryptInPlace(file)) {
          converted++;
        } else {
          alreadyDone++;
        }
      } catch (IOException | RuntimeException e) {
        // Named and carried on: one unreadable file must not strand the other 378. The original
        // is untouched, so a re-run picks it up once whatever broke is fixed.
        log.warn("Migration skipped {}: {}", file, e.getMessage(), e);
        failed.add(file + " — " + e.getMessage());
      }
    }
    return new Outcome(OP_MIGRATE, converted, alreadyDone, failed);
  }

  /**
   * Opens every encrypted file and checks it yields the length its header claims.
   *
   * @param keyFile the keyring
   * @param uploadRoot the store
   * @return how many verified, how many are still plaintext, and what failed
   * @throws IOException if the store cannot be walked
   */
  public static Outcome verify(Path keyFile, Path uploadRoot) throws IOException {
    EncryptedAssetService service =
        new EncryptedAssetService(
            new FileMasterKeyProvider(keyFile), EnvelopeHeader.DEFAULT_BLOCK_SIZE);
    int verified = 0;
    int stillPlaintext = 0;
    List<String> failed = new ArrayList<>();
    for (Path file : files(uploadRoot)) {
      if (!service.isEncrypted(file)) {
        stillPlaintext++;
        continue;
      }
      try (InputStream in = service.read(file)) {
        long expected = service.plainLength(file);
        long actual = in.transferTo(OutputStream.nullOutputStream());
        if (actual != expected) {
          failed.add(file + " — decrypted to " + actual + " bytes, header says " + expected);
        } else {
          verified++;
        }
      } catch (IOException | RuntimeException e) {
        log.warn("Verification failed for {}: {}", file, e.getMessage(), e);
        failed.add(file + " — " + e.getMessage());
      }
    }
    return new Outcome(OP_VERIFY, verified, stillPlaintext, failed);
  }

  private static List<Path> files(Path root) throws IOException {
    if (!Files.isDirectory(root)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(root)) {
      return walk.filter(Files::isRegularFile)
          .filter(path -> !path.getFileName().toString().startsWith(".orz-"))
          .sorted(Comparator.naturalOrder())
          .toList();
    }
  }

  private static int report(Outcome outcome) {
    System.out.println(outcome.describe());
    outcome.failed().forEach(line -> System.out.println("  FAILED " + line));
    return outcome.failed().isEmpty() ? 0 : 1;
  }

  /**
   * What a run of the tool found.
   *
   * @param operation {@code migrate} or {@code verify}
   * @param changed converted files, or verified ones
   * @param unchanged files already done, or still plaintext
   * @param failed one line per file that could not be handled, naming why
   */
  public record Outcome(String operation, int changed, int unchanged, List<String> failed) {

    /** Compact canonical constructor enforcing the record's invariants (ERR-106). */
    public Outcome {
      failed = List.copyOf(failed == null ? List.of() : failed);
    }

    /** A line an operator can paste into a change record. */
    public String describe() {
      String changedLabel = OP_MIGRATE.equals(operation) ? "converted" : "verified";
      String unchangedLabel =
          OP_MIGRATE.equals(operation) ? "already encrypted" : "still plaintext";
      return operation
          + ": "
          + changed
          + " "
          + changedLabel
          + ", "
          + unchanged
          + " "
          + unchangedLabel
          + ", "
          + failed.size()
          + " failed";
    }
  }
}
