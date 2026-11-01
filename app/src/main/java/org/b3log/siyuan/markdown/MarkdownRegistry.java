package org.b3log.siyuan.markdown;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** One process-wide authority; all opens and first Save As writes pass through it. */
public final class MarkdownRegistry {
    public static final MarkdownRegistry INSTANCE = new MarkdownRegistry();
    private final Map<String, Claim> claims = new HashMap<>();
    private final Map<String, Boolean> sessions = new HashMap<>();

    public static final class Claim {
        public final String identity;
        public final String session;
        public final String reservation;
        public String operation;
        public boolean unresolved;
        public boolean reserved;
        public boolean written;
        private Claim(String identity, String session, String reservation) {
            this.identity = identity;
            this.session = session;
            this.reservation = reservation;
            this.reserved = !reservation.isEmpty();
        }
    }

    public synchronized void attach(String session) { sessions.put(session, true); }
    public synchronized boolean hasSessions() { return !sessions.isEmpty(); }
    public synchronized boolean isSessionActive(String session) { return sessions.containsKey(session); }
    public synchronized boolean hasProtectedClaims(String session) {
        for (Claim claim : claims.values()) if (claim.session.equals(session) && (claim.unresolved || claim.reserved && claim.written)) return true;
        return false;
    }
    public synchronized void abandonCrashed(String session) { sessions.remove(session); }
    public synchronized void takeRecovered(String identity, String previous, String next) throws IOException {
        Claim claim = claims.get(identity);
        if (claim == null) { claim(identity, next, false); return; }
        if (!claim.session.equals(previous) || isSessionActive(previous) || claim.unresolved || claim.reserved && claim.written) throw new IOException("unavailable");
        claims.put(identity, new Claim(identity, next, ""));
    }
    public synchronized String owner(String identity) { Claim claim = claims.get(identity); return claim == null ? null : claim.session; }

    public synchronized Claim claim(String identity, String session, boolean reserve) throws IOException {
        Claim prior = claims.get(identity);
        if (prior != null) {
            if (!prior.session.equals(session)) throw new IOException("occupied");
            return prior;
        }
        Claim claim = new Claim(identity, session, reserve ? UUID.randomUUID().toString() : "");
        claims.put(identity, claim);
        return claim;
    }

    public synchronized Claim require(String identity, String session, String reservation) throws IOException {
        Claim claim = claims.get(identity);
        if (claim == null || !claim.session.equals(session) || (!reservation.isEmpty() && !claim.reservation.equals(reservation))) throw new IOException("protocol");
        return claim;
    }

    public synchronized void begin(String identity, String session, String reservation, String operation) throws IOException {
        Claim claim = require(identity, session, reservation);
        if (claim.unresolved && !operation.equals(claim.operation)) throw new IOException("unavailable");
        if (claim.written || claim.reserved && claim.operation != null && !claim.operation.equals(operation)) throw new IOException("protocol");
        claim.operation = operation;
        claim.unresolved = true;
    }

    public synchronized void resolved(String identity, String session, String operation) throws IOException {
        Claim claim = require(identity, session, "");
        if (!operation.equals(claim.operation)) throw new IOException("protocol");
        claim.unresolved = false;
    }

    public synchronized void reserveOperation(String identity, String session, String operation) throws IOException {
        Claim claim = require(identity, session, "");
        if (!claim.reserved || claim.unresolved || claim.written || claim.operation != null && !claim.operation.equals(operation)) throw new IOException("protocol");
        claim.operation = operation;
    }

    public synchronized void written(String identity, String session, String operation) throws IOException {
        resolved(identity, session, operation);
        Claim claim = require(identity, session, "");
        if (claim.reserved) claim.written = true;
    }

    public synchronized void bind(String identity, String session, String operation) throws IOException {
        Claim claim = require(identity, session, "");
        if (claim.unresolved || !claim.written || !claim.reserved || !operation.equals(claim.operation)) throw new IOException("protocol");
        claim.reserved = false; claim.written = false;
    }

    public synchronized void release(String identity, String session) throws IOException {
        Claim claim = require(identity, session, "");
        if (claim.unresolved || claim.reserved && claim.written) throw new IOException("unavailable");
        claims.remove(identity);
    }

    public synchronized void detach(String session) throws IOException {
        for (Claim claim : claims.values()) if (claim.session.equals(session) && (claim.unresolved || claim.reserved && claim.written)) throw new IOException("unavailable");
        claims.values().removeIf(claim -> claim.session.equals(session));
        sessions.remove(session);
    }
}
