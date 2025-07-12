package com.blooming.inpeak.answer.dto.request;

import com.blooming.inpeak.answer.dto.command.AnswerCreateAsyncCommand;
import jakarta.validation.constraints.NotNull;

public record AnswerCreateRequest(
        @NotNull
        String audioURL,
        @NotNull
        Long time,
        @NotNull
        Long questionId,
        @NotNull
        Long interviewId,
        String videoURL
    ){

    public AnswerCreateAsyncCommand toAsyncCommand(Long memberId) {
        return AnswerCreateAsyncCommand
            .builder()
            .audioURL(audioURL)
            .time(time)
            .memberId(memberId)
            .questionId(questionId)
            .interviewId(interviewId)
            .videoURL(videoURL)
            .build();
    }
}
