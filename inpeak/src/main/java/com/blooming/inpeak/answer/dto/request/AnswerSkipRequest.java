package com.blooming.inpeak.answer.dto.request;

import jakarta.validation.constraints.NotNull;

public record AnswerSkipRequest(
    @NotNull Long questionId,
    @NotNull Long interviewId
) {
}
