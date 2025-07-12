package com.blooming.inpeak.answer.domain;

public enum AnswerTaskStatus {
    WAITING("메시지만 보낸 상태"),
    SUCCESS("Answer 생성 완료"),
    FAILED("GPT 또는 처리 오류"),
    ;

    private final String message;

    AnswerTaskStatus(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
