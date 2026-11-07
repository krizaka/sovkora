package com.krizaka.sovkora.jobs.domain.model;

/**
 * Why a job failed, <b>declared by whoever failed it</b> — never inferred from its message.
 *
 * <p>The fifth application of AGENTS.md §12. The executor is the only party that knows whether it
 * refused a payload, lost its model, or was stopped by a gate; every consumer downstream of it was
 * reading prose and guessing. ADR-053 reopened the question that {@code ADR-046 §2} closed, for a
 * reason that is not billing: a SENSITIVE pack's audit log could not tell <i>"we protected the
 * user"</i> from <i>"we broke"</i>, because a scope-guard refusal and a model outage arrived
 * identical.
 *
 * <p><b>Two axes decide the vocabulary</b>, and a category exists only when it answers differently
 * on one of them: did the platform do its job correctly, and does the answer say the user was at
 * fault. A third — what an operator must go and fix — is what the audit log needs, and it is why
 * three causes that settle identically are still three.
 *
 * <table>
 *   <caption>The vocabulary, and what each answer commits to</caption>
 *   <tr><th>Cause</th><th>Settles</th><th>Blames the input</th><th>Operator action</th></tr>
 *   <tr><td>{@link #GUARD_REFUSAL}</td><td><b>yes</b></td><td>no</td><td>none — working as designed</td></tr>
 *   <tr><td>{@link #INPUT_INVALID}</td><td><b>yes</b></td><td><b>yes</b></td><td>none</td></tr>
 *   <tr><td>{@link #EXECUTOR_FAULT}</td><td>no</td><td>no</td><td>fix our code</td></tr>
 *   <tr><td>{@link #PLATFORM_UNAVAILABLE}</td><td>no</td><td>no</td><td>restore a dependency</td></tr>
 *   <tr><td>{@link #TIMEOUT}</td><td>no</td><td>no</td><td>capacity, or the bound itself</td></tr>
 * </table>
 *
 * <p><b>The settlement rule is one sentence: we bill for work we performed correctly, and never for
 * our own failure.</b> A gate declining to serve and a payload the executor could not use are both
 * the platform behaving exactly as designed, so the compute already spent is settled. A defect, an
 * absent dependency and a missed deadline are ours, so the whole hold goes back.
 *
 * <p>An earlier draft billed only {@link #INPUT_INVALID}, on the ground that ADR-051 §3 had made a
 * refusal free. That reading does not survive contact with a multi-step run: it makes a refusal
 * free <i>only when it happens first</i>, so an actor's bill would depend on which step an
 * out-of-scope phrase happened to reach — and it made a refusal and a model outage settle
 * identically, which is precisely the confusion this ADR exists to end. Under the rule as written,
 * a refusal that arrives before any work still costs nothing, because nothing was measured.
 */
public enum FailureCause {

  /**
   * A gate declined to serve, on purpose.
   *
   * <p>Settles what the run had already measured, and nothing more. The platform did exactly what
   * it was built to do — including telling the actor, in the pack's own words, what it will not
   * answer — so the work performed before that point was performed correctly. A refusal that
   * arrives before anything ran still costs nothing, because there is nothing to settle.
   *
   * <p>Which gate refused travels as detail, not as a category: an entitlement refusal and a scope
   * refusal are the same event to the saga, and promoting one gate into the vocabulary while the
   * others stay generic is the asymmetry that makes a vocabulary grow without bound.
   */
  GUARD_REFUSAL,

  /**
   * The payload could not be used, and the executor can say so from its own validation.
   *
   * <p>Settles what ran, like {@link #GUARD_REFUSAL} — and unlike it, this is the cause that
   * <b>says the user's input was at fault</b>, which is the distinction the audit trail keeps. It
   * must be set from a <b>declared</b> rejection — a schema violation, a missing required field, an
   * unreadable asset the executor opened — never from an exception that merely happened while
   * handling user data. ADR-046 §2's counter-example stands: <i>"compose requires at least one
   * readable photo"</i> read exactly like bad input and was our own defect three layers deep. That
   * failure is {@link #EXECUTOR_FAULT}, and an executor that cannot tell the two apart must say
   * {@code EXECUTOR_FAULT}.
   */
  INPUT_INVALID,

  /**
   * The executor itself broke: a defect, an unhandled case, a crash.
   *
   * <p>Releases everything, and it is the <b>default for silence</b> — see {@link #of}. We do not
   * bill and do not accuse for want of information.
   */
  EXECUTOR_FAULT,

  /**
   * Something the executor depends on was not there: a model host, a broker, a database.
   *
   * <p>Distinct from {@link #EXECUTOR_FAULT} despite settling identically, because the audit log
   * exists to say what happened and "our code is wrong" and "Ollama was down" are two different
   * mornings.
   */
  PLATFORM_UNAVAILABLE,

  /**
   * The work did not finish inside its bound.
   *
   * <p>Releases everything. A timeout is genuinely ambiguous — an oversized input and a slow host
   * produce the same event — and where the two failure modes are asymmetric the guard falls on the
   * side of the party who is not in the room (AGENTS.md §12). Billing for our own slowness is the
   * error that cannot be taken back.
   */
  TIMEOUT;

  /**
   * The cause named on the wire, or {@link #EXECUTOR_FAULT} when none was.
   *
   * <p><b>Absence is never a refusal and never a user's fault.</b> An unknown or missing value
   * degrades to the cause that releases the hold and blames nobody, which is the only degradation
   * that cannot cost an actor money it should not have.
   *
   * @param declared what the producer put on {@code job.{id}.error}, possibly {@code null}
   * @return the declared cause, or {@code EXECUTOR_FAULT}
   */
  public static FailureCause of(String declared) {
    if (declared == null || declared.isBlank()) {
      return EXECUTOR_FAULT;
    }
    for (FailureCause candidate : values()) {
      if (candidate.name().equalsIgnoreCase(declared.trim())) {
        return candidate;
      }
    }
    return EXECUTOR_FAULT;
  }

  /**
   * Whether a run that ended this way settles what its steps measured.
   *
   * @return {@code true} where the platform performed correctly — {@link #GUARD_REFUSAL} and {@link
   *     #INPUT_INVALID} — and {@code false} where the failure was ours
   */
  public boolean settlesMeasuredWork() {
    return this == GUARD_REFUSAL || this == INPUT_INVALID;
  }

  /**
   * Whether this answer attributes the failure to what the actor sent.
   *
   * <p>Separate from {@link #settlesMeasuredWork()} on purpose: a refusal and an invalid payload
   * bill the same and <b>read very differently</b> in a protected pack's trail, which is the whole
   * reason ADR-046 §2 was reopened. One says we protected someone; the other says they sent us
   * something we could not use.
   *
   * @return {@code true} only for {@link #INPUT_INVALID}
   */
  public boolean blamesTheInput() {
    return this == INPUT_INVALID;
  }
}
