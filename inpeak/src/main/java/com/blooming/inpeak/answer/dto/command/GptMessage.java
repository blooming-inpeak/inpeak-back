package com.blooming.inpeak.answer.dto.command;

public record GptMessage(
    String role,
    Object content
){ }
