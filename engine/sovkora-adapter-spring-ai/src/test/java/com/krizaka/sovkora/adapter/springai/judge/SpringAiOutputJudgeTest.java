package com.krizaka.sovkora.adapter.springai.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krizaka.sovkora.core.domain.model.judge.JudgeRequest;
import com.krizaka.sovkora.core.domain.model.judge.JudgeVerdict;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

class SpringAiOutputJudgeTest {

  private static final JudgeRequest REQUEST =
      new JudgeRequest("Who signed?", "Alex signed.", List.of("Alex signed the lease on 3 May."));

  @Test
  void aSupportedAnswerPassesWithTheEvaluatorsScore() {
    Evaluator evaluator = mock(Evaluator.class);
    when(evaluator.evaluate(any()))
        .thenReturn(new EvaluationResponse(true, 0.9f, "supported", Map.of()));

    JudgeVerdict verdict = new SpringAiOutputJudge(evaluator).judge(REQUEST);

    assertThat(verdict.pass()).isTrue();
    assertThat(verdict.score()).isEqualTo(0.9f);
    assertThat(verdict.feedback()).isEqualTo("supported");
    assertThat(verdict.drift()).isCloseTo(0.1f, org.assertj.core.api.Assertions.offset(0.0001f));
  }

  @Test
  void aFailingAnswerWithoutAScoreCountsAsFullDrift() {
    Evaluator evaluator = mock(Evaluator.class);
    when(evaluator.evaluate(any()))
        .thenReturn(new EvaluationResponse(false, "not supported", Map.of()));

    JudgeVerdict verdict = new SpringAiOutputJudge(evaluator).judge(REQUEST);

    assertThat(verdict.pass()).isFalse();
    assertThat(verdict.drift()).isEqualTo(1f);
  }

  @Test
  void theQuestionTheContextAndTheAnswerReachTheEvaluator() {
    Evaluator evaluator = mock(Evaluator.class);
    when(evaluator.evaluate(any())).thenReturn(new EvaluationResponse(true, "", Map.of()));

    new SpringAiOutputJudge(evaluator).judge(REQUEST);

    var captor = ArgumentCaptor.forClass(EvaluationRequest.class);
    verify(evaluator).evaluate(captor.capture());
    assertThat(captor.getValue().getUserText()).isEqualTo("Who signed?");
    assertThat(captor.getValue().getResponseContent()).isEqualTo("Alex signed.");
    assertThat(captor.getValue().getDataList())
        .extracting(Document::getText)
        .containsExactly("Alex signed the lease on 3 May.");
  }

  @Test
  void aScoreOutsideTheUnitIntervalIsRefused() {
    assertThatThrownBy(() -> new JudgeVerdict(true, 1.5f, ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
