package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.application.routing.ProviderClassifier;
import com.krizaka.sovkora.core.domain.model.chat.ChatRoute;
import com.krizaka.sovkora.core.domain.ports.outbound.UserCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decides which model answers a turn: the user's own key for a commercial provider, else ours. */
public final class ChatRoutes {

  private static final Logger logger = LoggerFactory.getLogger(ChatRoutes.class);

  private ChatRoutes() {}

  /**
   * Resolves the route of a turn.
   *
   * @param userId the user
   * @param provider the resolved provider
   * @param credentials where the user's keys are read
   * @return the user's key route when the provider is commercial and a key exists, else the
   *     platform
   */
  public static ChatRoute resolve(
      String userId, String provider, UserCredentialsProvider credentials) {
    if (!ProviderClassifier.isCommercial(provider)) {
      return ChatRoute.platform();
    }
    var apiKey = credentials.getDecryptedApiKey(userId, provider);
    if (apiKey.isEmpty()) {
      logger.warn(
          "No API key found for user {} and provider {}, falling back to local model.",
          userId,
          provider);
      return ChatRoute.platform();
    }
    return ChatRoute.userKey(provider, apiKey.get());
  }
}
