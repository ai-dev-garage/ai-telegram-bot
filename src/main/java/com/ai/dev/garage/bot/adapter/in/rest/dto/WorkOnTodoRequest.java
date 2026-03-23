package com.ai.dev.garage.bot.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkOnTodoRequest {
    @NotBlank
    @Pattern(regexp = "agent|plan", message = "mode must be 'agent' or 'plan'")
    private String mode;
    private String workspace;
}
