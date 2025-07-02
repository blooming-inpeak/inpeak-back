package com.blooming.inpeak.answer.dto.response;

import java.util.List;

public record AnswerListResponse(
    List<AnswerResponse> AnswerResponseList,
    boolean hasNext
) { }
