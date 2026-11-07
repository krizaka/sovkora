package com.krizaka.sovkora.knowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Knowledge service (Phase 4): owns RAG retrieval, tool RAG-source lookup, and async indexing over
 * its own database ({@code orazaka_knowledge_db}). Internal-only — reached over HTTP by the core's
 * {@code KnowledgeService} port and over AMQP for {@code job.rag.*} indexing commands.
 */
@SpringBootApplication
public class KnowledgeServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(KnowledgeServiceApplication.class, args);
  }
}
