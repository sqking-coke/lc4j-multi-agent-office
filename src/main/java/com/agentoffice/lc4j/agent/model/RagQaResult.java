package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;

public record RagQaResult(
    String answer,
    double confidence,
    List<String> sources,
    List<String> relatedQuestions
) implements Serializable {}
