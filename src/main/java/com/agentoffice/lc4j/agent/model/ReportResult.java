package com.agentoffice.lc4j.agent.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record ReportResult(
    String summary,
    Map<String, Object> metrics,
    List<Trend> trends,
    List<Anomaly> anomalies,
    List<String> recommendations
) implements Serializable {
    public record Trend(String metric, String direction, String change) implements Serializable {}
    public record Anomaly(String metric, double value, double expected) implements Serializable {}
}
