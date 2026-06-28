package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;

public record TicketResult(
    String category,
    int urgency,
    List<String> entities,
    String suggestion,
    String autoReply
) implements Serializable {}
