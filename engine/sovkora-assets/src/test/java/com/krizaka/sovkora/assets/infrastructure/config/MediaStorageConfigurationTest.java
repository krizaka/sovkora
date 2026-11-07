package com.krizaka.sovkora.assets.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.storage.BunnyAssetStore;
import com.krizaka.sovkora.assets.infrastructure.storage.DirectoryAssetStore;
import com.krizaka.sovkora.assets.infrastructure.storage.NoDurableAssetStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The context loads with the deploy configuration: the environment variables of the dev app. */
class MediaStorageConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AssetEncryptionConfiguration.class))
          .withBean(
              MasterKeyProvider.class,
              () ->
                  new MasterKeyProvider() {
                    public String activeKeyId() {
                      return "k";
                    }

                    public WrappedKey wrap(javax.crypto.SecretKey key) {
                      return new WrappedKey("k", key.getEncoded());
                    }

                    public javax.crypto.SecretKey unwrap(String id, byte[] wrapped) {
                      return new javax.crypto.spec.SecretKeySpec(wrapped, "AES");
                    }
                  });

  @Test
  void localIsTheDefault_soNothingChangesUntilAProviderIsChosen() {
    runner.run(
        context -> {
          assertThat(context).hasSingleBean(DurableAssetStore.class);
          assertThat(context.getBean(DurableAssetStore.class))
              .isInstanceOf(NoDurableAssetStore.class);
          assertThat(context.getBean(DurableAssetStore.class).enabled()).isFalse();
        });
  }

  @Test
  void bunnyIsChosenByTheMediaStorageVariables() {
    // MEDIA_STORAGE_PROVIDER / _ZONE / _API_KEY / _ENDPOINT bind to media.storage.*
    runner
        .withPropertyValues(
            "media.storage.provider=bunny",
            "media.storage.zone=sovkora-dev",
            "media.storage.api-key=k",
            "media.storage.endpoint=storage.bunnycdn.com",
            "media.storage.tenant=sovkora")
        .run(
            context -> {
              assertThat(context.getBean(DurableAssetStore.class))
                  .isInstanceOf(BunnyAssetStore.class);
              assertThat(context.getBean(MediaStorageProperties.class).tenant())
                  .isEqualTo("sovkora");
            });
  }

  @Test
  void directoryIsChosenByItsVariable() {
    runner
        .withPropertyValues("media.storage.provider=directory", "media.storage.directory=target/d")
        .run(
            context ->
                assertThat(context.getBean(DurableAssetStore.class))
                    .isInstanceOf(DirectoryAssetStore.class));
  }

  @Test
  void anIncompleteBunnyConfigurationFailsAtStart_byName() {
    runner
        .withPropertyValues("media.storage.provider=bunny")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasRootCauseMessage(
                        "media.storage.provider=bunny needs MEDIA_STORAGE_ZONE and MEDIA_STORAGE_API_KEY"));
  }

  @Test
  void anUnknownProviderFailsAtStart() {
    runner
        .withPropertyValues("media.storage.provider=ftp")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void theKeyNeverReachesToString() {
    MediaStorageProperties props =
        new MediaStorageProperties("bunny", "z", "super-secret", "e", null, "t");
    assertThat(props.toString()).doesNotContain("super-secret");
  }
}
