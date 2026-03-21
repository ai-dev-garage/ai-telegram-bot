package com.ai.dev.garage.bot.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobRequest {
    @NotBlank
    private String intent;
    @Valid
    @NotNull
    private Requester requester;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Requester {
        @NotNull
        private Long telegramUserId;
        private String telegramUsername;
        @NotNull
        private Long telegramChatId;
    }
}
