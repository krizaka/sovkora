package com.krizaka.sovkora.assets.infrastructure.config;

import com.krizaka.sovkora.assets.application.service.EncryptedAssetService;
import com.krizaka.sovkora.assets.domain.port.DurableAssetStore;
import com.krizaka.sovkora.assets.domain.port.MasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.adapter.FileMasterKeyProvider;
import com.krizaka.sovkora.assets.infrastructure.storage.BunnyAssetStore;
import com.krizaka.sovkora.assets.infrastructure.storage.DirectoryAssetStore;
import com.krizaka.sovkora.assets.infrastructure.storage.NoDurableAssetStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires asset encryption into any service that stores assets.
 *
 * <p>{@link MasterKeyProvider} is {@code @ConditionalOnMissingBean} so a deployment can supply a
 * KMS-backed one without touching a line of this. That is the point of the port: the local path is
 * the same code path as the one that matters (ADR-054 §3).
 */
@AutoConfiguration
@EnableConfigurationProperties({AssetEncryptionProperties.class, MediaStorageProperties.class})
public class AssetEncryptionConfiguration {

  private static final Logger logger = LoggerFactory.getLogger(AssetEncryptionConfiguration.class);

  /**
   * The local keyring, unless something else already provided a key source.
   *
   * @param properties where the keyring lives
   * @return a file-backed provider
   */
  @Bean
  @ConditionalOnMissingBean(MasterKeyProvider.class)
  public MasterKeyProvider masterKeyProvider(AssetEncryptionProperties properties) {
    return new FileMasterKeyProvider(properties.resolvedMasterKeyFile());
  }

  /**
   * Reads and writes the store.
   *
   * @param masterKeyProvider wraps the per-file data keys
   * @param properties the block size, and the two states worth shouting about
   * @return the store
   */
  @Bean
  public EncryptedAssetService encryptedAssetService(
      MasterKeyProvider masterKeyProvider, AssetEncryptionProperties properties) {
    if (!properties.enabled()) {
      logger.warn(
          "Asset encryption is DISABLED: new assets are written in the clear. This is a migration"
              + " state, and finding #13 is open again for as long as it lasts.");
    }
    if (properties.acceptPlaintext()) {
      logger.warn(
          "The asset read path is accepting PLAINTEXT files. This is for the duration of a"
              + " migration only — a permanent tolerance is a permanent door (ADR-054 §5).");
    }
    return new EncryptedAssetService(masterKeyProvider, properties.blockSize());
  }

  /**
   * The durable second copy chosen by {@code MEDIA_STORAGE_PROVIDER}; local disk only by default.
   *
   * @param properties the provider and its coordinates
   * @return the store
   */
  @Bean
  @ConditionalOnMissingBean(DurableAssetStore.class)
  public DurableAssetStore durableAssetStore(MediaStorageProperties properties) {
    return switch (properties.provider()) {
      case "bunny" -> {
        logger.info("Assets are kept durably in the Bunny zone {} (private)", properties.zone());
        yield new BunnyAssetStore(properties.endpoint(), properties.zone(), properties.apiKey());
      }
      case "directory" -> new DirectoryAssetStore(java.nio.file.Path.of(properties.directory()));
      default -> new NoDurableAssetStore();
    };
  }
}
