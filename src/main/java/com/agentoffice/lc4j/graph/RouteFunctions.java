package com.agentoffice.lc4j.graph;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.EdgeAction;

/**
 * LangGraph4j 条件路由函数 —— 替代手写版 7 种 Strategy 的分支逻辑。
 *
 * <p>每个函数返回下一个 node 名称，LangGraph4j 据此在图中跳转。</p>
 */
@Slf4j
public class RouteFunctions {

    /**
     * 根据协同模式路由到不同的执行分支。
     *
     * <p>PARALLEL → fanout → 并行分发
     * SERIAL/RELAY → agent_executor → 串行执行
     * CONDITION → condition_check → 条件判断
     * LOOP → agent_executor → 循环执行</p>
     */
    public static EdgeAction<OfficeAgentState> routeByCoopMode() {
        return state -> {
            String coopMode = state.getCoopMode();
            log.info("[{}] Routing by coopMode: {}", state.getTraceId(), coopMode);
            return switch (coopMode != null ? coopMode : "SERIAL") {
                case "PARALLEL" -> "fanout";
                case "SERIAL", "RELAY" -> "agent_executor";
                case "CONDITION" -> "condition_check";
                case "LOOP" -> "agent_executor";
                default -> "agent_executor";
            };
        };
    }

    /**
     * 循环退出判定。
     *
     * @return "agent_executor" 继续循环 / "aggregator" 退出
     */
    public static EdgeAction<OfficeAgentState> routeForLoop() {
        return state -> {
            int loopCount = state.getLoopCount();
            int maxLoop = state.getMaxLoop();
            double qualityScore = state.getQualityScore();

            log.info("[{}] Loop: count={}/{}, score={}", state.getTraceId(), loopCount, maxLoop, qualityScore);

            if (loopCount >= maxLoop) {
                log.info("[{}] Max iterations reached → aggregator", state.getTraceId());
                return "aggregator";
            }
            if (qualityScore >= 0.8) {
                log.info("[{}] Quality target reached → aggregator", state.getTraceId());
                return "aggregator";
            }
            return "agent_executor";
        };
    }

    /**
     * 条件分支路由。
     */
    public static EdgeAction<OfficeAgentState> routeByScore() {
        return state -> {
            String nextAction = state.getNextAction();
            log.info("[{}] Conditional routing: nextAction={}", state.getTraceId(), nextAction);
            return nextAction != null ? nextAction : "aggregator";
        };
    }
}
