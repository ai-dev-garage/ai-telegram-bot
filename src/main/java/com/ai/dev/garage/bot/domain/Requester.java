package com.ai.dev.garage.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Requester {
    @Column(name = "requester_user_id", nullable = false)
    private Long telegramUserId;
    @Column(name = "requester_username")
    private String telegramUsername;
    @Column(name = "requester_chat_id", nullable = false)
    private Long telegramChatId;
}
