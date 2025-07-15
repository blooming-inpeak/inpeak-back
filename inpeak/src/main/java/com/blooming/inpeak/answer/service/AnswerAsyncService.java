package com.blooming.inpeak.answer.service;

import com.blooming.inpeak.answer.domain.AnswerTask;
import com.blooming.inpeak.answer.dto.command.AnswerCreateAsyncCommand;
import com.blooming.inpeak.answer.repository.AnswerTaskRepository;
import com.blooming.inpeak.question.domain.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerAsyncService {

    private final AnswerManagerService answerManagerService;
    private final AnswerTaskRepository answerTaskRepository;
    private final AnswerAsyncProcessor answerAsyncProcessor;

    /**
     * 비동기 답변 생성 요청 메서드
     *
     * @param command 답변 생성 명령어
     * @return 생성된 답변 작업 ID
     */
    @Transactional
    public Long requestAsyncAnswerCreation(AnswerCreateAsyncCommand command) {

        Question question = answerManagerService.validateAndGetQuestion(command);
        AnswerTask newTask = AnswerTask.createAnswerTask(command, question.getContent());
        AnswerTask savedTask = answerTaskRepository.save(newTask);

        // 비동기 작업 요청
        answerAsyncProcessor.handleTaskAsync(savedTask);

        return savedTask.getId();
    }
}
