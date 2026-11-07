package com.krizaka.sovkora.knowledgeservice.infrastructure.adapter.rest.dto;

/**
 * Request for semantic context retrieval.
 *
 * @param query the search text
 * @param topK number of documents to retrieve (defaulted to 3 when null or non-positive)
 */
public record RetrieveRequest(String query, Integer topK) {

  public int resolvedTopK() {
    return topK == null || topK <= 0 ? 3 : topK;
  }
}
