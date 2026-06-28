package com.agentoffice.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 告警服务：通过 Webhook（如企业微信/钉钉）发送分级告警通知。
 * 未配置 webhook-url 时降级为日志输出。
 */
@Slf4j
@Component
public class AlertService {

    private final String webhookUrl;
    private final HttpClient httpClient;

    public AlertService(@Value("${alert.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 发送告警：webhook 未配置时降级为日志 Warn 输出。 */
    public void sendAlert(String title, String content, String level) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("[ALERT] {} ({}): {}", title, level, content);
            return;
        }

        try {
            String body = String.format("""
                    {"msgtype": "markdown", "markdown": {"title": "%s", "text": "## %s\\n> 级别: %s\\n> %s"}}
                    """, title, title, level, content);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.error("Failed to send alert: {}", e.getMessage());
        }
    }

    public void alertAgentFailure(String agentCode, int consecutiveFailures) {
        sendAlert("Agent连续失败告警",
                "Agent: " + agentCode + "\n连续失败次数: " + consecutiveFailures,
                "P1");
    }

    public void alertTokenBudget(String agentCode, int usedPercent) {
        sendAlert("Token预算预警",
                "Agent: " + agentCode + "\n当前用量: " + usedPercent + "%",
                usedPercent >= 100 ? "P1" : "P2");
    }

    public void alertQueueBacklog(int queueSize, int threshold) {
        sendAlert("任务队列积压告警",
                "当前队列积压: " + queueSize + "\n阈值: " + threshold,
                "P2");
    }
}
