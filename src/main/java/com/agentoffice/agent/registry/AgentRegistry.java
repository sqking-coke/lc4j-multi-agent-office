package com.agentoffice.agent.registry;

import com.agentoffice.agent.AgentConfig;
import com.agentoffice.agent.BizAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AgentRegistry {

    private final ConcurrentHashMap<String, BizAgent> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentConfig> configs = new ConcurrentHashMap<>();

    public void register(BizAgent agent, AgentConfig config) {
        agents.put(agent.getAgentCode(), agent);
        configs.put(agent.getAgentCode(), config);
        log.info("Agent registered: {} ({})", agent.getAgentCode(), config.getAgentName());
    }

    public void unregister(String agentCode) {
        agents.remove(agentCode);
        configs.remove(agentCode);
        log.info("Agent unregistered: {}", agentCode);
    }

    public Optional<BizAgent> getAgent(String agentCode) {
        return Optional.ofNullable(agents.get(agentCode));
    }

    public Optional<AgentConfig> getConfig(String agentCode) {
        return Optional.ofNullable(configs.get(agentCode));
    }

    public List<BizAgent> getAllAgents() {
        return new ArrayList<>(agents.values());
    }

    public List<AgentConfig> getAllConfigs() {
        return new ArrayList<>(configs.values());
    }

    public Optional<BizAgent> findBestForCapability(String capability) {
        return agents.values().stream()
                .filter(a -> a.getCapability().getCapabilities().contains(capability))
                .filter(a -> configs.get(a.getAgentCode()) != null)
                .min(Comparator.comparingInt(a -> configs.get(a.getAgentCode()).getPriority()));
    }

    public List<BizAgent> findAllForCapabilities(List<String> capabilities) {
        return agents.values().stream()
                .filter(a -> a.getCapability().getCapabilities().stream().anyMatch(capabilities::contains))
                .sorted(Comparator.comparingInt(a -> configs.get(a.getAgentCode()).getPriority()))
                .collect(Collectors.toList());
    }

    public int getAgentCount() { return agents.size(); }

    public int getEnabledCount() {
        return (int) agents.values().stream().filter(a -> a.healthCheck()).count();
    }

    public boolean containsCapability(String capability) {
        return agents.values().stream().anyMatch(a -> a.getCapability().getCapabilities().contains(capability));
    }

    public Set<String> getAllCapabilities() {
        return agents.values().stream()
                .flatMap(a -> a.getCapability().getCapabilities().stream())
                .collect(Collectors.toSet());
    }
}
