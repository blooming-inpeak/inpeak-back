package com.blooming.inpeak.answer.controller;

import com.blooming.inpeak.answer.dto.command.AnswerCreateAsyncCommand;
import com.blooming.inpeak.answer.dto.request.AnswerCreateRequest;
import com.blooming.inpeak.answer.dto.response.AnswerByTaskResponse;
import com.blooming.inpeak.answer.dto.response.AnswerDetailResponse;
import com.blooming.inpeak.answer.service.AnswerAsyncService;
import com.blooming.inpeak.answer.service.AnswerService;
import com.blooming.inpeak.member.dto.MemberPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/answer")
@RequiredArgsConstructor
public class AnswerControllerV2 {

    private final AnswerService answerService;
    private final AnswerAsyncService answerAsyncService;

    // TODO: ECR cicd를 위한 주석 추가
    @PostMapping("/create")
    public ResponseEntity<Long> createAnswer(
        @AuthenticationPrincipal MemberPrincipal memberPrincipal,
        @RequestBody AnswerCreateRequest request
    ) {
        AnswerCreateAsyncCommand command = request.toAsyncCommand(memberPrincipal.id());
        Long response = answerAsyncService.requestAsyncAnswerCreation(command);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<AnswerByTaskResponse> findAnswerByTaskId(
        @AuthenticationPrincipal MemberPrincipal memberPrincipal,
        @PathVariable Long taskId) {
        return answerService.findAnswerByTaskId(taskId, memberPrincipal.id());
    }

    @GetMapping("/{answerId}")
    public ResponseEntity<AnswerDetailResponse> getAnswerById(
        @PathVariable Long answerId,
        @AuthenticationPrincipal MemberPrincipal memberPrincipal
    ) {
        AnswerDetailResponse response = answerService.getAnswerById(answerId, memberPrincipal.id());
        return ResponseEntity.ok(response);
    }
}
