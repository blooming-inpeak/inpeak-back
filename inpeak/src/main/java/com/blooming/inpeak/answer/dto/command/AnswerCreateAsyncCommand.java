package com.blooming.inpeak.answer.dto.command;

import com.blooming.inpeak.answer.domain.AnswerTask;
import lombok.Builder;

@Builder
public record AnswerCreateAsyncCommand(
    String audioURL,
    Long time,
    Long memberId,
    Long questionId,
    Long interviewId,
    String videoURL
){
    public static AnswerCreateAsyncCommand from (AnswerTask answerTask) {
        return AnswerCreateAsyncCommand
            .builder()
            .audioURL(answerTask.getAudioFileUrl())
            .time(answerTask.getTime())
            .memberId(answerTask.getMemberId())
            .questionId(answerTask.getQuestionId())
            .interviewId(answerTask.getInterviewId())
            .videoURL(answerTask.getVideoUrl())
            .build();
    }
}
