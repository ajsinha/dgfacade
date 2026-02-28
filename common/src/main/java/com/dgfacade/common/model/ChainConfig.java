/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 * Proprietary and confidential.
 */
package com.dgfacade.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Declarative pipeline chain configuration.
 * A chain defines a sequence of handler steps that execute in order,
 * with support for conditional execution, parallel fan-out, and
 * flexible payload mapping between steps.
 *
 * Loaded from config/chains/*.json files.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChainConfig {

    @JsonProperty("chain_id")
    private String chainId;

    @JsonProperty("description")
    private String description;

    @JsonProperty("ttl_minutes")
    private int ttlMinutes = 30;

    @JsonProperty("error_strategy")
    private ErrorStrategy errorStrategy = ErrorStrategy.ABORT;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("steps")
    private List<ChainStep> steps;

    public enum ErrorStrategy { ABORT, SKIP, FALLBACK }
    public enum MergeStrategy { REPLACE, MERGE_PREV, APPEND, PASSTHROUGH }
    public enum JoinStrategy { MERGE_ALL, KEYED, FIRST_SUCCESS }

    /**
     * A single step in a chain pipeline.
     * Can be a sequential handler invocation or a parallel fan-out group.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChainStep {

        @JsonProperty("step")
        private int step;

        @JsonProperty("handler")
        private String handler;

        @JsonProperty("alias")
        private String alias;

        @JsonProperty("description")
        private String description;

        @JsonProperty("payload_mapping")
        private Map<String, Object> payloadMapping;

        @JsonProperty("merge_strategy")
        private MergeStrategy mergeStrategy = MergeStrategy.REPLACE;

        @JsonProperty("error_strategy")
        private ErrorStrategy errorStrategy;

        @JsonProperty("fallback_value")
        private Map<String, Object> fallbackValue;

        @JsonProperty("when")
        private String when;

        @JsonProperty("parallel")
        private List<ChainStep> parallel;

        @JsonProperty("join_strategy")
        private JoinStrategy joinStrategy = JoinStrategy.KEYED;

        // --- Getters and Setters ---
        public int getStep() { return step; }
        public void setStep(int step) { this.step = step; }
        public String getHandler() { return handler; }
        public void setHandler(String handler) { this.handler = handler; }
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getPayloadMapping() { return payloadMapping; }
        public void setPayloadMapping(Map<String, Object> payloadMapping) { this.payloadMapping = payloadMapping; }
        public MergeStrategy getMergeStrategy() { return mergeStrategy; }
        public void setMergeStrategy(MergeStrategy mergeStrategy) { this.mergeStrategy = mergeStrategy; }
        public ErrorStrategy getErrorStrategy() { return errorStrategy; }
        public void setErrorStrategy(ErrorStrategy errorStrategy) { this.errorStrategy = errorStrategy; }
        public Map<String, Object> getFallbackValue() { return fallbackValue; }
        public void setFallbackValue(Map<String, Object> fallbackValue) { this.fallbackValue = fallbackValue; }
        public String getWhen() { return when; }
        public void setWhen(String when) { this.when = when; }
        public List<ChainStep> getParallel() { return parallel; }
        public void setParallel(List<ChainStep> parallel) { this.parallel = parallel; }
        public JoinStrategy getJoinStrategy() { return joinStrategy; }
        public void setJoinStrategy(JoinStrategy joinStrategy) { this.joinStrategy = joinStrategy; }

        public boolean isParallel() { return parallel != null && !parallel.isEmpty(); }

        public String effectiveAlias() {
            return alias != null ? alias : (handler != null ? handler.toLowerCase() : "step_" + step);
        }
    }

    // --- Getters and Setters ---
    public String getChainId() { return chainId; }
    public void setChainId(String chainId) { this.chainId = chainId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getTtlMinutes() { return ttlMinutes; }
    public void setTtlMinutes(int ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    public ErrorStrategy getErrorStrategy() { return errorStrategy; }
    public void setErrorStrategy(ErrorStrategy errorStrategy) { this.errorStrategy = errorStrategy; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public List<ChainStep> getSteps() { return steps; }
    public void setSteps(List<ChainStep> steps) { this.steps = steps; }
}
