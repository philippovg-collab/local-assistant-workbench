package com.example.demo.model;

import java.util.List;

public record InstructionRevisionDiff(
    String instructionId,
    int fromRevision,
    int toRevision,
    List<RevisionDiffEntry> changes
) {
}
