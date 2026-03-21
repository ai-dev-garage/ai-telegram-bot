package com.ai.dev.garage.bot.adapter.in.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoResponse {
    private Long id;
    private String title;
    private String description;
    private String status;
    private String source;
    private String workspace;
    private Long linkedJobId;
    private String createdAt;
    private String updatedAt;
}
