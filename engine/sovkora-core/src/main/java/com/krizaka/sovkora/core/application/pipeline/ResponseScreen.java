package com.krizaka.sovkora.core.application.pipeline;

import com.krizaka.sovkora.core.application.interceptor.PromptContextInterceptor;
import com.krizaka.sovkora.core.domain.model.PromptContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the output screens ({@link PromptContextInterceptor#screenResponse}) on an answer about to
 * be served — a fresh model answer and a cached one take exactly the same path.
 *
 * <p>A refusal ({@link PipelineShortCircuitException}) always propagates. Any other failure follows
 * the posture the interceptor declared (ADR-064): a control ({@code failsClosed}) stops the turn,
 * an enrichment is logged and the answer passes through that screen unchanged.
 */
public final class ResponseScreen {

  private static final Logger logger = LoggerFactory.getLogger(ResponseScreen.class);

  private ResponseScreen() {}

  /**
   * Screens an answer.
   *
   * @param interceptors the engine's interceptors, in order
   * @param context the turn's final prompt context (null when no pipeline ran: nothing to read)
   * @param responseText the answer about to be served
   * @return the text to serve
   * @throws PipelineShortCircuitException when a screen refuses the answer
   */
  public static String apply(
      List<PromptContextInterceptor> interceptors, PromptContext context, String responseText) {
    return screen(interceptors, context, responseText).text();
  }

  /**
   * Screens an answer and collects the notes the screens add (verdict, badge).
   *
   * @param interceptors the engine's interceptors, in order
   * @param context the turn's final prompt context (null when no pipeline ran)
   * @param responseText the answer about to be served
   * @return the text to serve and the notes for the response metadata
   * @throws PipelineShortCircuitException when a screen refuses the answer
   */
  public static ScreenedResponse screen(
      List<PromptContextInterceptor> interceptors, PromptContext context, String responseText) {
    return run(interceptors, context, responseText, PromptContextInterceptor::screen);
  }

  /**
   * Final screen of a streamed answer, on the assembled text: the notes for the final event.
   *
   * @param interceptors the engine's interceptors, in order
   * @param context the turn's final prompt context (null when no pipeline ran)
   * @param assembled the whole streamed answer
   * @return the assembled text and the notes for the final event
   * @throws PipelineShortCircuitException when a screen refuses the answer
   */
  public static ScreenedResponse screenStreamed(
      List<PromptContextInterceptor> interceptors, PromptContext context, String assembled) {
    return run(interceptors, context, assembled, PromptContextInterceptor::screenStreamed);
  }

  /**
   * Screens one streamed chunk before it is emitted.
   *
   * @param interceptors the engine's interceptors, in order
   * @param context the turn's final prompt context (null when no pipeline ran)
   * @param textSoFar everything streamed so far, the chunk included
   * @throws PipelineShortCircuitException when a screen refuses the answer
   */
  public static void screenChunk(
      List<PromptContextInterceptor> interceptors, PromptContext context, String textSoFar) {
    run(
        interceptors,
        context,
        textSoFar,
        (interceptor, ctx, text) -> {
          interceptor.screenChunk(ctx, text);
          return ScreenedResponse.plain(text);
        });
  }

  /** One screen of one interceptor. */
  @FunctionalInterface
  private interface Screen {
    ScreenedResponse apply(
        PromptContextInterceptor interceptor, PromptContext context, String text);
  }

  private static ScreenedResponse run(
      List<PromptContextInterceptor> interceptors,
      PromptContext context,
      String responseText,
      Screen screen) {
    if (context == null || responseText == null) {
      return ScreenedResponse.plain(responseText);
    }
    String text = responseText;
    Map<String, Object> notes = new HashMap<>();
    for (PromptContextInterceptor interceptor : interceptors) {
      ScreenedResponse screened = runOne(interceptor, screen, context, text);
      if (screened != null) {
        text = screened.text() == null ? text : screened.text();
        notes.putAll(screened.notes());
      }
    }
    return new ScreenedResponse(text, notes);
  }

  /**
   * One screen of one interceptor. A refusal always propagates; any other failure follows the
   * posture the interceptor declared: a control stops the turn, an enrichment is logged.
   *
   * @return the screened answer, or {@code null} when an enrichment failed and left it as it was
   */
  private static ScreenedResponse runOne(
      PromptContextInterceptor interceptor, Screen screen, PromptContext context, String text) {
    try {
      return screen.apply(interceptor, context, text);
    } catch (PipelineShortCircuitException refusal) {
      throw refusal;
    } catch (RuntimeException failure) {
      if (interceptor.failsClosed()) {
        throw failure;
      }
      logger.error(
          "Output screen '{}' failed — serving the answer as it was.",
          interceptor.getClass().getSimpleName(),
          failure);
      return null;
    }
  }

  /**
   * Tells every interceptor a fresh answer passed the screens and is served ({@code
   * responseServed}). A failure is logged and never reaches the user.
   */
  public static void served(
      List<PromptContextInterceptor> interceptors, PromptContext context, String servedText) {
    if (context == null || servedText == null) {
      return;
    }
    for (PromptContextInterceptor interceptor : interceptors) {
      try {
        interceptor.responseServed(context, servedText);
      } catch (RuntimeException failure) {
        logger.warn(
            "responseServed of '{}' failed — the turn is unaffected.",
            interceptor.getClass().getSimpleName(),
            failure);
      }
    }
  }
}
