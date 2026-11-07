package com.krizaka.sovkora.business.api;

/** CQRS pivot of an {@link Intention}: {@code COMMAND} mutates state, {@code QUERY} only reads. */
public enum IntentionType {
  COMMAND,
  QUERY
}
