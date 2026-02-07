/*
 * Copyright © 2025-2030, All Rights Reserved
 * Ashutosh Sinha | Email: ajsinha@gmail.com
 *
 * This software is proprietary and confidential. Unauthorized copying,
 * distribution, modification, or use is strictly prohibited without
 * explicit written permission from the copyright holder.
 * Patent Pending.
 */
package com.dgfacade.server.service;

import com.dgfacade.common.model.HandlerStatus;
import com.dgfacade.common.model.StreamingSession;
import com.dgfacade.server.actor.StreamingHandlerActor;
import org.apache.pekko.actor.typed.ActorRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Manages all active streaming handler sessions.
 * Tracks both session metadata and actor references for stop/management.
 */
@Service
public class StreamingSessionManager {

    private static final Logger log = LoggerFactory.getLogger(StreamingSessionManager.class);
    private final Map<String, StreamingSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, ActorRef<StreamingHandlerActor.StreamingCommand>> actorRefs = new ConcurrentHashMap<>();

    public void register(StreamingSession session) {
        activeSessions.put(session.getSessionId(), session);
        log.info("Registered streaming session: {}", session);
    }

    public void registerActorRef(String sessionId, ActorRef<StreamingHandlerActor.StreamingCommand> ref) {
        actorRefs.put(sessionId, ref);
    }

    public void unregister(String sessionId) {
        StreamingSession removed = activeSessions.remove(sessionId);
        actorRefs.remove(sessionId);
        if (removed != null) {
            log.info("Unregistered streaming session: {}", removed.getSessionId());
        }
    }

    public Optional<StreamingSession> getSession(String sessionId) {
        return Optional.ofNullable(activeSessions.get(sessionId));
    }

    public Optional<ActorRef<StreamingHandlerActor.StreamingCommand>> getActorRef(String sessionId) {
        return Optional.ofNullable(actorRefs.get(sessionId));
    }

    public Collection<StreamingSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }

    public Collection<StreamingSession> getSessionsByHandler(String handlerType) {
        return activeSessions.values().stream()
                .filter(s -> s.getHandlerType().equalsIgnoreCase(handlerType))
                .collect(Collectors.toList());
    }

    public int getActiveCount() {
        return activeSessions.size();
    }

    public void updateStatus(String sessionId, HandlerStatus status) {
        StreamingSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
        }
    }
}
