package com.blooming.inpeak.answer.service;

import com.blooming.inpeak.answer.domain.Answer;
import com.blooming.inpeak.answer.domain.AnswerStatus;
import com.blooming.inpeak.answer.dto.command.AnswerCreateCommand;
import com.blooming.inpeak.answer.dto.command.AnswerFilterCommand;
import com.blooming.inpeak.answer.dto.response.AnswerDetailResponse;
import com.blooming.inpeak.answer.dto.response.AnswerIDResponse;
import com.blooming.inpeak.answer.dto.response.AnswerListResponse;
import com.blooming.inpeak.answer.dto.response.AnswerResponse;
import com.blooming.inpeak.answer.dto.response.InterviewWithAnswersResponse;
import com.blooming.inpeak.answer.dto.response.RecentAnswerListResponse;
import com.blooming.inpeak.answer.dto.response.RecentAnswerResponse;
import com.blooming.inpeak.answer.repository.AnswerRepository;
import com.blooming.inpeak.answer.repository.AnswerRepositoryCustom;
import com.blooming.inpeak.common.error.exception.ConflictException;
import com.blooming.inpeak.common.error.exception.EncodingException;
import com.blooming.inpeak.common.error.exception.ForbiddenException;
import com.blooming.inpeak.common.error.exception.NotFoundException;
import com.blooming.inpeak.interview.domain.Interview;
import com.blooming.inpeak.interview.repository.InterviewRepository;
import com.blooming.inpeak.member.service.MemberStatisticsService;
import com.blooming.inpeak.question.domain.Question;
import com.blooming.inpeak.question.repository.QuestionRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final AnswerRepositoryCustom answerRepositoryCustom;
    private final GPTService gptService;
    private final QuestionRepository questionRepository;
    private final InterviewRepository interviewRepository;
    private final MemberStatisticsService memberStatisticsService;

    /**
     * 답변을 스킵하는 메서드
     *
     * @param memberId    사용자 ID
     * @param questionId  답변 ID
     * @param interviewId 인터뷰 ID
     */
    @Transactional
    public AnswerIDResponse skipAnswer(Long memberId, Long questionId, Long interviewId) {
        if (answerRepository.existsByInterviewIdAndQuestionId(interviewId, questionId)) {
            throw new ConflictException("이미 답변이 존재하는 질문입니다.");
        }

        Answer skippedAnswer = Answer.ofSkipped(memberId, questionId, interviewId);
        answerRepository.save(skippedAnswer);

        return new AnswerIDResponse(skippedAnswer.getId());
    }

    /**
     * 답변을 불러오는 메서드 인자에 따라 다른 값을 불러온다.
     *
     * @param command 검색 조건
     * @return 답변들과 페이징 정보
     */
    public AnswerListResponse getAnswerList(AnswerFilterCommand command) {
        Pageable pageable = PageRequest.of(command.page(), command.size());

        // 공통된 로직: 답변 리스트 가져오기
        Slice<AnswerResponse> results = answerRepositoryCustom.findAnswers(
            command.memberId(),
            command.isUnderstood(),
            command.status(),
            command.sortType(),
            pageable
        );

        return new AnswerListResponse(results.getContent(), results.hasNext());
    }

    /**
     * 해당 날짜에 진행한 인터뷰에 대한 답변 리스트 반환
     *
     * @param memberId 사용자 ID
     * @param date     날짜
     * @return 인터뷰 ID, 답변 ID, 질문 제목 등
     */
    public InterviewWithAnswersResponse getAnswersByDate(Long memberId, LocalDate date) {
        List<Answer> answers = answerRepository.findAnswersByMemberAndDate(memberId, date);

        if (answers.isEmpty()) {
            // 🔍 인터뷰는 존재하지만 답변이 없는 케이스 확인을 위해 인터뷰만 따로 조회
            Interview interview = interviewRepository.findByMemberIdAndStartDate(memberId, date)
                .orElseThrow(() -> new NotFoundException("해당 날짜에 진행된 인터뷰가 없습니다."));

            // 🔴 인터뷰는 있지만 답변이 없음
            throw new ConflictException("해당 인터뷰에 대한 답변이 존재하지 않습니다.");
        }

        // ✅ 인터뷰도 있고, 답변도 있음
        Interview interview = answers.get(0).getInterview(); // answer가 있으므로 get(0) 안전
        return InterviewWithAnswersResponse.from(interview, answers);
    }

    /**
     * 답변 상태를 기준으로 필터링하여 최근 3개의 답변 리스트를 반환하는 메서드
     *
     * @param memberId 회원 ID
     * @param status   필터링 할 답변 상태
     * @return 최근 3개의 답변 리스트
     */
    public RecentAnswerListResponse getRecentAnswers(Long memberId, AnswerStatus status) {
        List<Answer> answers = answerRepositoryCustom.findRecentAnswers(memberId, status);

        List<RecentAnswerResponse> responseList = answers.stream()
            .map(RecentAnswerResponse::from)
            .toList();

        return RecentAnswerListResponse.from(responseList);
    }

    /**
     * 답변을 생성하는 메서드
     *
     * @param command 답변 생성 명령
     */
    @Transactional
    public AnswerIDResponse createAnswer(AnswerCreateCommand command) {
        if (answerRepository.existsByInterviewIdAndQuestionId(command.interviewId(),
            command.questionId())) {
            throw new ConflictException("이미 답변이 존재하는 질문입니다.");
        }

        Question question = questionRepository.findById(command.questionId())
            .orElseThrow(() -> new NotFoundException("해당 질문이 존재하지 않습니다."));

        String feedback = gptService.makeGPTResponse(command.audioFile(), question.getContent());

        Answer answer = Answer.of(command, feedback);
        answerRepository.save(answer);

        // 회원 통계 업데이트
        memberStatisticsService.updateStatistics(command.memberId(), answer.getStatus());

        return new AnswerIDResponse(answer.getId());
    }

    private String encodeToBase64(MultipartFile file) {
        try {
            return Base64.getEncoder().encodeToString(file.getBytes());
        } catch (IOException e) {
            throw new EncodingException("파일 인코딩 실패");
        }
    }

    /**
     * 답변의 상태를 업데이트하는 메서드
     *
     * @param answerId 답변 ID
     * @param isUnderstood 이해 여부
     * @param memberId  사용자 ID
     */
    @Transactional
    public void updateUnderstood(Long answerId, boolean isUnderstood, Long memberId) {
        Answer answer = answerRepository.findById(answerId)
            .orElseThrow(() -> new NotFoundException("해당 답변이 존재하지 않습니다."));

        if (!answer.getMemberId().equals(memberId)) {
            throw new ForbiddenException("해당 답변에 대한 접근 권한이 없습니다.");
        }

        answer.setUnderstood(isUnderstood);
        answerRepository.save(answer);
    }

    /**
     * 답변에 코멘트를 추가하는 메서드
     *
     * @param answerId 답변 ID
     * @param comment  코멘트
     */
    @Transactional
    public void updateComment(Long answerId, String comment) {
        Answer answer = answerRepository.findById(answerId)
            .orElseThrow(() -> new NotFoundException("해당 답변이 존재하지 않습니다."));

        answer.setComment(comment);
        answerRepository.save(answer);
    }

    /**
     * 특정 질문에 대한 답변을 조회하는 메서드
     *
     * @param interviewId 인터뷰 ID
     * @param questionId  질문 ID
     * @param memberId    사용자 ID
     * @return 답변 상세 정보
     */
    public AnswerDetailResponse getAnswer(Long interviewId, Long questionId, Long memberId) {
        Answer answer = answerRepository.findByInterviewIdAndQuestionId(interviewId, questionId)
            .orElseThrow(() -> new NotFoundException("해당 답변이 존재하지 않습니다."));

        if (!answer.getMemberId().equals(memberId)) {
            throw new ForbiddenException("해당 답변에 대한 접근 권한이 없습니다.");
        }

        return AnswerDetailResponse.from(answer);
    }

    /**
     * 특정 질문에 대한 답변을 조회하는 메서드
     *
     * @param answerId   답변 ID
     * @param memberId   사용자 ID
     * @return 답변 상세 정보
     */
    public AnswerDetailResponse getAnswerById(Long answerId, Long memberId) {
        Answer answer = answerRepository.findById(answerId)
            .orElseThrow(() -> new NotFoundException("해당 답변이 존재하지 않습니다."));

        if (!answer.getMemberId().equals(memberId)) {
            throw new ForbiddenException("해당 답변에 대한 접근 권한이 없습니다.");
        }

        return AnswerDetailResponse.from(answer);
    }
}
