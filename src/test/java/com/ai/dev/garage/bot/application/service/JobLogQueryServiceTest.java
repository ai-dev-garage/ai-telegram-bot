package com.ai.dev.garage.bot.application.service;

import com.ai.dev.garage.bot.application.port.out.JobLogStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobLogQueryServiceTest {

    @Mock
    private JobLogStore jobLogStore;

    @InjectMocks
    private JobLogQueryService jobLogQueryService;

    @Test
    void shouldReturnTailFromStoreWhenTailRequested() {
        var lines = List.of("a", "b");
        when(jobLogStore.findLinesTail(10L, 100)).thenReturn(lines);

        var result = jobLogQueryService.getTail(10L, 100);

        assertThat(result).isEqualTo(lines);
        verify(jobLogStore).findLinesTail(10L, 100);
    }
}
