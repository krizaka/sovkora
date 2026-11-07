package com.krizaka.sovkora.adapter.springai.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationView;

/**
 * Puts the feature key of the turn on every {@code gen_ai} observation as the low-cardinality tag
 * {@code orazaka.feature} (ADR-094 slice 3, #171), so the cost dashboard reads tokens and latency
 * per feature from the same source. A turn is opened by {@link #openTurn}; its children (the chat
 * call, the tool rounds) find the key in their parent chain. A call outside any turn (an embedding,
 * a speech synthesis) wears {@code none}, so every series has the same tag keys.
 *
 * <p>The key comes from the engine's own feature table (a bounded set, never a user value), and
 * only the key is tagged — never the tenant, the user or any text (ADR-064).
 */
public class FeatureTagObservationFilter implements ObservationFilter {

  /** The metric tag. */
  public static final String TAG = "orazaka.feature";

  /** The tag value of a call that belongs to no feature. */
  public static final String NONE = "none";

  private static final String TURN = "orazaka.turn";
  private static final int MAX_LENGTH = 64;
  private static final String[] OBSERVED = {"gen_ai.", "spring.ai.", "db.vector."};

  /**
   * Opens the observation of a turn of a feature; hold it open while the model and its tools run.
   *
   * @param registry the host's registry
   * @param featureKey the feature of the turn, or {@code null}
   * @return the started observation (a no-op one without a host registry)
   */
  public static Observation openTurn(ObservationRegistry registry, String featureKey) {
    String feature = clean(featureKey);
    Observation.Context context = new Observation.Context();
    context.put(TAG, feature);
    return Observation.createNotStarted(TURN, () -> context, registry)
        .lowCardinalityKeyValue(TAG, feature)
        .start();
  }

  @Override
  public Observation.Context map(Observation.Context context) {
    if (isObserved(context.getName())) {
      context.addLowCardinalityKeyValue(
          KeyValue.of(TAG, featureOf(context.getParentObservation())));
    }
    return context;
  }

  private static boolean isObserved(String name) {
    if (name == null) {
      return false;
    }
    for (String prefix : OBSERVED) {
      if (name.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String featureOf(ObservationView parent) {
    ObservationView view = parent;
    while (view != null) {
      Observation.ContextView context = view.getContextView();
      String feature = context.get(TAG);
      if (feature != null) {
        return feature;
      }
      view = context.getParentObservation();
    }
    return NONE;
  }

  private static String clean(String featureKey) {
    if (featureKey == null || featureKey.isBlank()) {
      return NONE;
    }
    String trimmed = featureKey.strip();
    return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
  }
}
