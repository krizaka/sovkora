package com.krizaka.sovkora.persistence;

import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.entity.ChatSessionEntity;
import com.krizaka.sovkora.persistence.infrastructure.adapter.persistence.repository.ChatSessionRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal Spring Boot configuration for persistence-app slice tests. The module is a library (no
 * {@code @SpringBootApplication}), so {@code @DataJpaTest} needs an explicit
 * {@code @SpringBootConfiguration} to bootstrap the JPA context against the Testcontainers
 * PostgreSQL provided by {@link com.krizaka.sovkora.test.AbstractContainerIntegrationTest}.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackageClasses = ChatSessionEntity.class)
@EnableJpaRepositories(basePackageClasses = ChatSessionRepository.class)
public class PersistenceTestApplication {}
