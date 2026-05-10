package com.example.demo.service.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.demo.config.ContextProperties;
import com.example.demo.error.ApplicationException;
import com.example.demo.service.memory.port.MemoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemoryEntryServiceTest {

    @Test
    void disabledLongTermMemoryBlocksReviewEndpointsBeforeRepositoryAccess() {
        MemoryRepository repository = Mockito.mock(MemoryRepository.class);
        MemoryEntryService service = new MemoryEntryService(
            new ContextProperties(),
            repository,
            new MemorySafetyPolicy()
        );

        ApplicationException exception = assertThrows(
            ApplicationException.class,
            () -> service.list(null, null, null, null)
        );

        assertEquals("memory.disabled", exception.getCode());
        verifyNoInteractions(repository);
    }
}
