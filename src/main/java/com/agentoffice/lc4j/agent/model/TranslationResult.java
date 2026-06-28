package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;

public record TranslationResult(
    String sourceLanguage,
    String targetLanguage,
    String translatedText,
    List<String> alternatives,
    double confidence
) implements Serializable {}
