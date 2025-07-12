package com.blooming.inpeak.answer.service;

import com.blooming.inpeak.answer.domain.Answer;
import com.blooming.inpeak.answer.domain.AnswerTask;
import com.blooming.inpeak.answer.dto.command.AnswerCreateAsyncCommand;
import com.blooming.inpeak.answer.repository.AnswerTaskRepository;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnswerAsyncProcessor {

    private final AnswerTaskRepository answerTaskRepository;
    private final AnswerManagerService answerManagerService;
    private final GptAsyncService gptAsyncService;
    private final AnswerPresignedUrlService answerPresignedUrlService;

    @Async("gptExecutor")
    public CompletableFuture<Void> processAnswerTaskAsync(AnswerTask task) {
        AnswerCreateAsyncCommand command = AnswerCreateAsyncCommand.from(task);
        byte[] audioBytes = answerPresignedUrlService.downloadAudioFromS3(command.audioURL());

        try {
            String feedback = gptAsyncService.makeGptAsyncResponse(audioBytes, task.getQuestionContent());
            Answer answer = answerManagerService.generateAnswer(command, feedback);
            task.markSuccess(answer.getId());

        } catch (Exception e) {
            task.markFailed();
        } finally {
            answerTaskRepository.save(task);
        }

        return CompletableFuture.completedFuture(null);
    }
}
