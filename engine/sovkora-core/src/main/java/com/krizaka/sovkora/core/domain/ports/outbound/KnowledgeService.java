package com.krizaka.sovkora.core.domain.ports.outbound;

import com.krizaka.sovkora.core.domain.model.security.Actor;

/**
 * Outbound port to the knowledge context (RAG retrieval + tool source lookup), owned by the
 * knowledge service since Phase 4. Implementations degrade explicitly: when the knowledge service
 * is unreachable or holds no matching content, they return an empty string — the pipeline enriches
 * with nothing instead of failing the request.
 *
 * <p><b>Every read is made for an {@link Actor}</b> (ADR-091): the knowledge service reads only the
 * passages that tenant, user, roles and groups may read, and filters inside its query. There is no
 * overload without an actor, so no caller can ask for "everything".
 */
public interface KnowledgeService {

  /**
   * Retrieves relevant context for a given query from the vector store.
   *
   * @param actor who the retrieval is made for
   * @param query the user query
   * @param topK number of relevant documents to retrieve
   * @return formatted context, or an empty string when nothing is available
   */
  String retrieveContext(Actor actor, String query, int topK);

  /**
   * Searches the registered tool RAG sources by substring, scoped to a tool and to what the actor
   * may read.
   *
   * @param actor who the search is made for
   * @param toolId the tool the sources were registered for
   * @param query the search text; blank returns every readable source for the tool
   * @return the matching sources' content joined by blank lines, or an empty string
   */
  String searchSources(Actor actor, String toolId, String query);
}
