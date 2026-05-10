package com.example.demo.service.context;

import com.example.demo.model.ContextAssemblyDroppedItem;
import com.example.demo.model.ContextAssemblyHistoryItem;
import com.example.demo.model.ContextTokenBudget;
import java.util.List;

record ContextSelection(
    List<ContextAssemblyHistoryItem> selectedHistory,
    List<ContextAssemblyDroppedItem> droppedItems,
    ContextTokenBudget tokenBudget
) {
    ContextSelection {
        selectedHistory = selectedHistory == null ? List.of() : List.copyOf(selectedHistory);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
    }
}
