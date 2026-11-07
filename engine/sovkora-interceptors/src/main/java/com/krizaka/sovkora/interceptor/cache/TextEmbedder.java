package com.krizaka.sovkora.interceptor.cache;

/** Turns a turn into a vector (a fast local model: bge-m3 or nomic-embed-text). */
@FunctionalInterface
public interface TextEmbedder {

  /** The embedding of {@code text}. */
  float[] embed(String text);
}
