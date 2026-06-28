package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;

public record ReviewResult(
    int score,
    String language,
    List<Issue> issues,
    String summary
) implements Serializable {
    public record Issue(
        String severity,
        int line,
        String category,
        String description,
        String suggestion
    ) implements Serializable {}
}
