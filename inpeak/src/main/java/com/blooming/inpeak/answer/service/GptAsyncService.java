package com.blooming.inpeak.answer.service;

import com.blooming.inpeak.answer.dto.command.GptMessage;
import com.blooming.inpeak.answer.dto.request.GptRequest;
import com.blooming.inpeak.answer.dto.response.GPTResponse;
import com.blooming.inpeak.common.error.exception.GptApiException;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GptAsyncService {

    @Value("${openai.models.text}")
    private String model;

    @Value("${openai.api.url}")
    private String apiUrl;

    @Value("${openai.prompt}")
    private String prompt;

    @Value("${openai.models.format}")
    private String format;

    @Value("${openai.api.key}")
    private String openAiKey;

    private final RestTemplate restTemplate;
    private final RestTemplate whisperRestTemplate;

    public String transcribe(byte[] audioFile) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(openAiKey);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("model", "whisper-1");
        body.add("file", new ByteArrayResource(audioFile) {
            @Override
            @NonNull
            public String getFilename() {
                return "audio.wav";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = whisperRestTemplate.exchange(
                "https://api.openai.com/v1/audio/transcriptions",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {
                }
            );

            log.info("STT 응답처리 성공: {}", response.getBody());
            return (String) response.getBody().get("text");
        } catch (Exception e) {
            throw new GptApiException("STT 처리 실패");
        }
    }

    /**
     * GPT API를 이용하여 지원자의 음성 데이터를 텍스트로 변환하고, 면접 질문에 대한 답변을 생성한다.
     *
     * @param audioFile       지원자의 음성 데이터
     * @param questionContent 면접 질문
     * @return 면접 질문에 대한 답변
     */
    public String makeGptAsyncResponse(byte[] audioFile, String questionContent) {
        String transcribedText = transcribe(audioFile);

        GptRequest request = GptRequest.of(model,
            makePromptMessages(transcribedText, questionContent));

        try {
            GPTResponse response = restTemplate.postForObject(apiUrl, request, GPTResponse.class);

            log.info("GPT 응답 생성 성공: {}", response);
            return (String) response.choices().get(0).message().content();
        } catch (Exception e) {
            log.error("GPT 응답 생성 실패: {}", e.getMessage(), e);
            throw new GptApiException("GPT 응답 생성 실패");
        }
    }

    private List<GptMessage> makePromptMessages(String text, String questionContent) {
        return List.of(
            new GptMessage("system", prompt),
            new GptMessage("user", List.of(
                Map.of("type", "text", "text", "면접 질문: " + questionContent),
                Map.of("type", "text", "text", "지원자의 답변: " + text)
            ))
        );
    }
}
