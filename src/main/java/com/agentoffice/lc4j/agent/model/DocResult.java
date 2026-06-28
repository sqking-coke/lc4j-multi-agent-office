package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;

public record DocResult(
    String operation,
    String result,
    List<String> keyPoints
) implements Serializable {}
