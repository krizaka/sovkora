package com.krizaka.sovkora.persistence.bridge;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.tool.CheckSubjectPepperTool;
import com.krizaka.sovkora.crypto.SecretSealer;
import com.krizaka.sovkora.persistence.domain.ports.inbound.RuntimeConfigProvider;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

@DisplayName("the subject pepper is read from its sealed setting, and nowhere else")
class SealedCheckSubjectPepperAdapterTest {

  @TempDir Path dir;

  private MasterKeyProvider keys;
  private final Settings settings = new Settings();
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(SealedCheckSubjectPepperAdapter.class);

  @BeforeEach
  void keyring() throws Exception {
    Path keyFile = dir.resolve("master.key");
    FileMasterKeyProvider.addKey(keyFile, "k1");
    keys = new FileMasterKeyProvider(keyFile);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void detach() {
    logger.detachAppender(logs);
  }

  private SealedCheckSubjectPepperAdapter adapter() {
    return adapter(keys);
  }

  private SealedCheckSubjectPepperAdapter adapter(MasterKeyProvider keyring) {
    StaticListableBeanFactory beans = new StaticListableBeanFactory();
    if (keyring != null) {
      beans.addBean("keys", keyring);
    }
    ObjectProvider<MasterKeyProvider> provider = beans.getBeanProvider(MasterKeyProvider.class);
    return new SealedCheckSubjectPepperAdapter(settings, provider);
  }

  private String sealed(UUID scope, byte[] pepper) {
    return new SecretSealer(keys)
        .seal(scope, Base64.getUrlEncoder().withoutPadding().encodeToString(pepper));
  }

  private static byte[] bytes(int length) {
    byte[] pepper = new byte[length];
    for (int i = 0; i < length; i++) {
      pepper[i] = (byte) (i * 7 + 3);
    }
    return pepper;
  }

  @Test
  @DisplayName("a pepper sealed by the operator's tool opens to its 32 bytes")
  void opensWhatTheToolSeals() {
    String sealed = CheckSubjectPepperTool.sealNew(keys);
    settings.put(CheckSubjectPepperTool.SETTING_KEY, sealed);

    Optional<byte[]> pepper = adapter().pepper();

    assertThat(pepper).isPresent();
    assertThat(pepper.get()).hasSize(32);
    String expected = new SecretSealer(keys).open(CheckSubjectPepperTool.SCOPE, sealed);
    assertThat(Base64.getUrlEncoder().withoutPadding().encodeToString(pepper.get()))
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("the key and the scope are the tool's")
  void sameKeyAndScopeAsTheTool() {
    assertThat(SealedCheckSubjectPepperAdapter.SETTING_KEY)
        .isEqualTo(CheckSubjectPepperTool.SETTING_KEY);
    assertThat(SealedCheckSubjectPepperAdapter.SCOPE).isEqualTo(CheckSubjectPepperTool.SCOPE);
  }

  @Test
  @DisplayName("a sealed round trip gives the very bytes, a longer pepper included")
  void roundTrip() {
    byte[] pepper = bytes(48);
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        sealed(SealedCheckSubjectPepperAdapter.SCOPE, pepper));

    assertThat(adapter().pepper()).hasValueSatisfying(v -> assertThat(v).isEqualTo(pepper));
  }

  @Test
  @DisplayName("shorter than 32 bytes: empty, so no check runs")
  void shortIsEmpty() {
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        sealed(SealedCheckSubjectPepperAdapter.SCOPE, bytes(31)));

    assertThat(adapter().pepper()).isEmpty();
  }

  @Test
  @DisplayName("no row, or a blank one: empty")
  void absentIsEmpty() {
    assertThat(adapter().pepper()).isEmpty();
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, "  ");
    assertThat(adapter().pepper()).isEmpty();
  }

  @Test
  @DisplayName("sealed for another scope: it does not open, empty")
  void wrongScopeIsEmpty() {
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, sealed(UUID.randomUUID(), bytes(32)));

    assertThat(adapter().pepper()).isEmpty();
  }

  @Test
  @DisplayName("a value in clear is never taken as the pepper")
  void clearTextIsEmpty() {
    String clear = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(40));
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, clear);

    assertThat(adapter().pepper()).isEmpty();
  }

  @Test
  @DisplayName("sealed under another keyring: it does not open, empty")
  void otherKeyringIsEmpty() throws Exception {
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        sealed(SealedCheckSubjectPepperAdapter.SCOPE, bytes(32)));
    Path other = dir.resolve("other.key");
    FileMasterKeyProvider.addKey(other, "k2");

    assertThat(adapter(new FileMasterKeyProvider(other)).pepper()).isEmpty();
  }

  @Test
  @DisplayName("no keyring in the host: empty")
  void noKeyringIsEmpty() {
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        sealed(SealedCheckSubjectPepperAdapter.SCOPE, bytes(32)));

    assertThat(adapter(null).pepper()).isEmpty();
  }

  @Test
  @DisplayName("a sealed text that is not base64url inside: empty")
  void notBase64Inside() {
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        new SecretSealer(keys).seal(SealedCheckSubjectPepperAdapter.SCOPE, "not base64 at all!"));

    assertThat(adapter().pepper()).isEmpty();
  }

  @Test
  @DisplayName("neither the pepper nor its sealed text is ever logged, thrown or printed")
  void neverLogged() throws Exception {
    byte[] pepper = bytes(32);
    String pepperText = Base64.getUrlEncoder().withoutPadding().encodeToString(pepper);
    String sealedText = sealed(SealedCheckSubjectPepperAdapter.SCOPE, pepper);
    Path other = dir.resolve("other.key");
    FileMasterKeyProvider.addKey(other, "k2");
    List<String> shown = new ArrayList<>();

    // Every path: opens, short, wrong scope, other keyring, clear text, no keyring.
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, sealedText);
    SealedCheckSubjectPepperAdapter adapter = adapter();
    adapter.pepper();
    shown.add(adapter.toString());
    adapter(new FileMasterKeyProvider(other)).pepper();
    adapter(null).pepper();
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, pepperText);
    adapter().pepper();
    settings.put(SealedCheckSubjectPepperAdapter.SETTING_KEY, sealed(UUID.randomUUID(), bytes(32)));
    adapter().pepper();
    settings.put(
        SealedCheckSubjectPepperAdapter.SETTING_KEY,
        sealed(SealedCheckSubjectPepperAdapter.SCOPE, bytes(8)));
    adapter().pepper();

    assertThat(logs.list).isNotEmpty();
    for (ILoggingEvent event : logs.list) {
      shown.add(event.getFormattedMessage());
      for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
        shown.add(t.getClassName() + ": " + t.getMessage());
      }
      if (event.getArgumentArray() != null) {
        for (Object argument : event.getArgumentArray()) {
          shown.add(String.valueOf(argument));
        }
      }
    }
    String pepperHex = java.util.HexFormat.of().formatHex(pepper);
    for (String line : shown) {
      assertThat(line)
          .doesNotContain(pepperText)
          .doesNotContain(sealedText)
          .doesNotContain(pepperHex)
          .doesNotContain(new String(pepper, java.nio.charset.StandardCharsets.ISO_8859_1));
    }
  }

  /** The settings rows, as the persistence port reads them. */
  private static final class Settings implements RuntimeConfigProvider {

    private final Map<String, String> rows = new HashMap<>();

    void put(String key, String value) {
      rows.put(key, value);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
      return rows.containsKey(key) ? Boolean.parseBoolean(rows.get(key)) : defaultValue;
    }

    @Override
    public int getInt(String key, int defaultValue) {
      return rows.containsKey(key) ? Integer.parseInt(rows.get(key)) : defaultValue;
    }

    @Override
    public String getString(String key, String defaultValue) {
      return rows.getOrDefault(key, defaultValue);
    }
  }
}
