package com.example.demo.service.memory;

import com.example.demo.model.ContextAssemblyDroppedMemoryItem;
import com.example.demo.model.ContextAssemblyMemoryItem;
import java.util.List;

public record MemorySelection(
    boolean requested,
    String status,
    String degradedReason,
    List<ContextAssemblyMemoryItem> selectedMemory,
    List<ContextAssemblyDroppedMemoryItem> droppedMemory
) {
    public MemorySelection {
        selectedMemory = selectedMemory == null ? List.of() : List.copyOf(selectedMemory);
        droppedMemory = droppedMemory == null ? List.of() : List.copyOf(droppedMemory);
    }

    public static MemorySelection disabled(String reason) {
        return new MemorySelection(false, "disabled", reason, List.of(), List.of());
    }
}
