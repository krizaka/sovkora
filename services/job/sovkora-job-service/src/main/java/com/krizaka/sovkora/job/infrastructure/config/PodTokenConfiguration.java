package com.krizaka.sovkora.job.infrastructure.config;

import com.krizaka.sovkora.job.domain.model.PodTokenPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * The pod token's signer and lifetime policy (krizaka-internal#3645).
 *
 * <p>Signed with Spring Security's Nimbus encoder, the library krizaka-security verifies with:
 * krizaka-security's {@code ServiceTokenProvider} mints {@code SERVICE} tokens only, and a pod must
 * never hold one. Its verifier is not a {@code JwtDecoder} bean, so the kit's session decoder stays
 * the one every other request is read with ({@link SecurityConfig}).
 */
@Configuration
class PodTokenConfiguration {

  @Bean
  PodTokenPolicy podTokenPolicy(PodTokenProperties properties) {
    return properties.toPolicy();
  }

  @Bean
  JwtEncoder podTokenEncoder(PodTokenProperties properties) {
    return NimbusJwtEncoder.withSecretKey(properties.signingKey())
        .algorithm(MacAlgorithm.HS256)
        .jwkPostProcessor(jwk -> jwk.keyID(PodTokenPolicy.KEY_ID))
        .build();
  }
}
