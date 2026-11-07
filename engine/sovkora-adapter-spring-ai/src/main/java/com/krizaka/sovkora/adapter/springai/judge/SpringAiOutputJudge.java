package com.krizaka.sovkora.adapter.springai.judge;

import com.krizaka.sovkora.core.domain.model.judge.JudgeRequest;
import com.krizaka.sovkora.core.domain.model.judge.JudgeVerdict;
import com.krizaka.sovkora.core.domain.ports.outbound.OutputJudge;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.evaluation.Evaluator;

/**
 * {@link OutputJudge} over a Spring AI {@link Evaluator} ({@code FactCheckingEvaluator} or {@code
 * RelevancyEvaluator}, built on the judge model a catalogue row names). A verdict without a score
 * counts as 1 when it passes and 0 when it fails.
 */
public class SpringAiOutputJudge implements OutputJudge {

  private final Evaluator evaluator;

  /**
   * Creates the judge.
   *
   * @param evaluator the Spring AI evaluator that does the judging
   */
  public SpringAiOutputJudge(Evaluator evaluator) {
    this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
  }

  @Override
  public JudgeVerdict judge(JudgeRequest request) {
    List<Document> data = request.contexts().stream().map(Document::new).toList();
    EvaluationResponse response =
        evaluator.evaluate(new EvaluationRequest(request.question(), data, request.answer()));
    float score = response.getScore() > 0f ? response.getScore() : (response.isPass() ? 1f : 0f);
    return new JudgeVerdict(response.isPass(), Math.min(score, 1f), response.getFeedback());
  }
}
