package com.krizaka.sovkora.edge.infrastructure.support;

/**
 * One admitted call to a backend. It records one outcome: the first of these three methods counts,
 * the later ones do nothing, so a stream that breaks after its headers were judged is not counted
 * twice.
 */
public interface BackendCall {

  /** The backend answered with this status; its 502, 503 and 504 are failures. */
  void answered(int status);

  /** The backend could not be reached or did not answer in time. */
  void failed(Throwable cause);

  /**
   * The call ended for a reason that is not the backend's (the client left): nothing is recorded.
   */
  void released();
}
