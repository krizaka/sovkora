package com.krizaka.sovkora.adapter.springai;

import com.krizaka.sovkora.adapter.springai.prompt.ClasspathPromptTextSource;
import com.krizaka.sovkora.business.api.UseCase;
import com.krizaka.sovkora.business.api.UseCaseDispatcher;
import com.krizaka.sovkora.business.api.UseCaseRegistry;
import com.krizaka.sovkora.business.application.SpringUseCaseRegistry;
import com.krizaka.sovkora.business.application.UseCaseDispatcherImpl;
import com.krizaka.sovkora.business.prompt.MarkdownPromptResolver;
import com.krizaka.sovkora.business.prompt.PromptTextSource;
import com.krizaka.sovkora.business.usecases.chat.ChatAssistantUseCase;
import com.krizaka.sovkora.business.usecases.image.ImageGenerationUseCase;
import com.krizaka.sovkora.business.usecases.studio.StudioRunUseCase;
import com.krizaka.sovkora.core.domain.ports.inbound.AiClient;
import com.krizaka.sovkora.studio.domain.port.StudioRunClient;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * Spring Boot auto-configuration of the sovkora-business use-cases — the App Factory wiring. It
 * lives in the adapter because sovkora-business is pure Java.
 *
 * <p>Registers the prompt resolver, every {@link UseCase} bean, and the {@link UseCaseRegistry} /
 * {@link UseCaseDispatcher}. Beans are declared explicitly (no classpath scanning) so the App
 * Factory wires identically in both host apps (router and worker), which scan different packs.
 * Adding a use-case = a new {@code UseCase} class + one {@code @Bean} line here — no core/router
 * change.
 *
 * <p>Discovered at runtime via {@code META-INF/spring/AutoConfiguration.imports}.
 *
 * @since 1.0.0
 */
@AutoConfiguration
public class BusinessUseCasesAutoConfiguration {

  @Bean
  PromptTextSource promptTextSource(ResourceLoader resourceLoader) {
    return new ClasspathPromptTextSource(resourceLoader);
  }

  @Bean
  MarkdownPromptResolver markdownPromptResolver(PromptTextSource source) {
    return new MarkdownPromptResolver(source);
  }

  @Bean
  ChatAssistantUseCase chatAssistantUseCase(AiClient aiClient, MarkdownPromptResolver prompts) {
    return new ChatAssistantUseCase(aiClient, prompts);
  }

  @Bean
  ImageGenerationUseCase imageGenerationUseCase(AiClient aiClient) {
    return new ImageGenerationUseCase(aiClient);
  }

  /**
   * Registers the Studio run use-case (ADR-034 §9.2).
   *
   * <p>Always wired: the port is always satisfied, by the HTTP adapter when the marketplace is
   * deployed and by a no-op that refuses runs when it is not. A conditional bean here would put the
   * "is there a marketplace" branch back into every call site (ERR-127).
   *
   * @param studioRunClient the Tier-1 port, adapter chosen at bootstrap by sovkora-studio-client
   * @return the use-case that makes a Studio run a first-class Intention
   */
  @Bean
  StudioRunUseCase studioRunUseCase(StudioRunClient studioRunClient) {
    return new StudioRunUseCase(studioRunClient);
  }

  @Bean
  UseCaseRegistry useCaseRegistry(List<UseCase<?, ?>> useCases) {
    return new SpringUseCaseRegistry(useCases);
  }

  @Bean
  UseCaseDispatcher useCaseDispatcher(UseCaseRegistry useCaseRegistry) {
    return new UseCaseDispatcherImpl(useCaseRegistry);
  }
}
