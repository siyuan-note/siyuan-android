package org.b3log.siyuan.markdown;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Native authority behind the shared frontend contract. Called on one I/O executor. */
public final class MarkdownHost {
    public final String sessionId;
    public final MarkdownFiles files;
    public final MarkdownStore store;
    private final MarkdownStore preferencesStore;
    private final Map<String, MarkdownFiles.Target> targets = new HashMap<>();
    private final MarkdownRegistry registry;
    private String committedOperation;
    private String committedSignature;
    private long committedRevision = -1;
    private long lastWriteRevision = -1;
    private long lastWriteSequence;
    private String instanceId;
    private MarkdownFiles.Target transferTarget;
    private long transferGeneration;
    private final Map<String, String> prepared = new HashMap<>();
    public volatile MarkdownFiles.Target target;
    public String content = "";
    public volatile long generation;
    public long revision;
    public boolean recovered;
    public volatile boolean closed;
    public volatile Uri resourceTree;

    public MarkdownHost(Context context) throws Exception {
        this(new MarkdownFiles(context),
                new MarkdownStore(new File(MarkdownPaths.directory(context), "recovery"), MarkdownDirectorySync::sync),
                new MarkdownStore(MarkdownPaths.directory(context), MarkdownDirectorySync::sync), MarkdownRegistry.INSTANCE);
    }

    MarkdownHost(MarkdownFiles files, MarkdownStore store, MarkdownRegistry registry) throws Exception {
        this(files, store, store, registry);
    }

    MarkdownHost(MarkdownFiles files, MarkdownStore store, MarkdownStore preferencesStore, MarkdownRegistry registry) throws Exception {
        this.files = files; this.store = store; this.registry = registry;
        this.preferencesStore = preferencesStore;
        sessionId = MarkdownIds.newId();
        for (String key : store.keys("")) {
            if (key.contains(sessionId)) throw new IOException("identifier collision");
        }
        // Restore unresolved claims before granting a fresh open/Save As to anyone.
        for (String key : store.keys("write-")) {
            try {
                MarkdownStore.Record record = store.read(key);
                if (record == null || record.payload.isEmpty()) continue;
                JSONObject pending = new JSONObject(record.payload);
                boolean writtenCandidate = "committed".equals(pending.optString("phase")) && pending.optBoolean("candidate");
                if (!"pending".equals(pending.optString("phase")) && !writtenCandidate) continue;
                String owner = pending.getString("sessionId"), identity = pending.getString("identity");
                MarkdownStore.Record binding = store.read("binding-" + owner);
                if (writtenCandidate && binding != null && pending.getString("operationId").equals(new JSONObject(binding.payload).optString("operationId"))) continue;
                registry.claim(identity, owner, pending.optBoolean("candidate"));
                registry.begin(identity, owner, "", pending.getString("operationId"));
                if (writtenCandidate) registry.written(identity, owner, pending.getString("operationId"));
            } catch (Exception ignored) { /* Preserve unreadable records; recovery UI reports them. */ }
        }
        registry.attach(sessionId);
    }

    public void open(Uri uri, int flags) throws Exception {
        MarkdownFiles.Target opened = files.open(uri, flags);
        openTarget(opened);
    }

    void openTarget(MarkdownFiles.Target opened) throws Exception {
        if (targets.containsKey(opened.handle)) throw new IOException("identifier collision");
        registry.claim(opened.identity, sessionId, false);
        target = opened; targets.put(opened.handle, opened); content = opened.initialContent;
    }

    public void discardPrepared() throws Exception { registry.detach(sessionId); closed = true; }

    public MarkdownFiles.Target candidate(Uri uri, int flags, JSONObject format) throws Exception {
        if (closed || targets.size() >= (target == null ? 1 : 2)) throw new IOException("unavailable");
        MarkdownFiles.Target selected = files.open(uri, flags);
        if (!selected.writable) throw new IOException("readonly");
        selected.bom = format.getBoolean("bom"); selected.newline = format.getString("newline");
        if (registry.owner(selected.identity) != null) throw new IOException("occupied");
        if (targets.putIfAbsent(selected.handle, selected) != null) throw new IOException("identifier collision");
        return selected;
    }

    public void cancelCandidate(String handle) throws Exception {
        MarkdownFiles.Target selected = targets.get(handle);
        if (selected == null) return;
        if (selected == target || registry.owner(selected.identity) != null) throw new IOException("unavailable");
        targets.remove(handle);
    }

    public JSONObject bootstrap(JSONObject languages, String lang, boolean dark) throws Exception {
        JSONObject value = new JSONObject().put("sessionId", sessionId).put("content", content)
                .put("preferences", preferences(null)).put("capabilities", new JSONObject().put("systemFonts", true)).put("languages", languages).put("lang", lang).put("platform", "android").put("dark", dark);
        if (target != null) value.put("target", target.json());
        if (recovered || revision > 0) value.put("recovered", new JSONObject().put("revision", revision).put("targetGeneration", generation));
        return value;
    }

    private MarkdownFiles.Target requestTarget(JSONObject request) throws Exception {
        if (closed || !sessionId.equals(request.getString("sessionId"))) throw new IOException("protocol");
        operationSequence(request);
        JSONObject claimed = request.getJSONObject("target");
        MarkdownFiles.Target result = targets.get(claimed.getString("handle"));
        if (result == null || !result.identity.equals(claimed.getString("identity"))) throw new IOException("permission");
        boolean candidate = request.has("reservationId");
        if (request.getLong("targetGeneration") != generation + (candidate ? 1 : 0) || (!candidate && result != target)) throw new IOException("protocol");
        if (candidate) {
            MarkdownRegistry.Claim reservation = registry.require(result.identity, sessionId, request.getString("reservationId"));
            if (!reservation.reserved || !request.getString("operationId").equals(reservation.operation)) throw new IOException("protocol");
        }
        return result;
    }

    private long operationSequence(JSONObject request) throws Exception {
        String prefix = sessionId + ":", operation = request.getString("operationId");
        if (!operation.startsWith(prefix)) throw new IOException("protocol");
        String[] parts = operation.substring(prefix.length()).split(":", -1);
        if (parts.length != 2 || !parts[0].matches("[a-zA-Z0-9_-]{1,128}") || !parts[1].matches("[1-9][0-9]{0,15}")) throw new IOException("protocol");
        long sequence = Long.parseLong(parts[1]);
        if (sequence > 9007199254740991L || instanceId != null && !instanceId.equals(parts[0])) throw new IOException("protocol");
        instanceId = parts[0];
        return sequence;
    }

    /** Only settled receipts are evictable; the watermark rejects their later replay. */
    private void pruneReceipts() {
        try {
            List<String> settled = new ArrayList<>();
            Map<String, Long> sequences = new HashMap<>();
            for (String key : store.keys("write-" + sessionId + "-")) {
                MarkdownStore.Record record = store.read(key);
                JSONObject entry = new JSONObject(record.payload);
                String phase = entry.optString("phase");
                if ("pending".equals(phase) || "committed".equals(phase) && entry.optBoolean("candidate")) continue;
                settled.add(key); sequences.put(key, entry.getLong("sequence"));
            }
            settled.sort(Comparator.comparingLong(sequences::get));
            for (int i = 0; i < settled.size() - 16; i++) store.remove(settled.get(i));
        } catch (Exception ignored) { /* A cleanup failure never changes the write receipt. */ }
    }

    private void cleanupClosedSession(String owner) {
        try {
            if (registry.isSessionActive(owner) || registry.hasProtectedClaims(owner)) return;
            for (String key : store.keys("write-" + owner + "-")) store.remove(key);
            store.remove("watermark-" + owner); store.remove("binding-" + owner); store.remove("switch-" + owner);
            MarkdownStore.Record draft = store.read("draft-" + owner);
            if (draft != null && draft.payload.isEmpty()) store.remove("draft-" + owner);
        } catch (Exception ignored) { /* Preserve data if a record cannot be inspected. */ }
    }

    private String signature(JSONObject request) throws Exception {
        JSONObject selected = request.getJSONObject("target"), format = selected.getJSONObject("format");
        return MarkdownCodec.digest(new JSONArray().put(request.getString("sessionId")).put(request.getString("operationId"))
                .put(request.getLong("targetGeneration")).put(request.getLong("revision")).put(request.getString("content"))
                .put(selected.getString("handle")).put(selected.getString("identity")).put(request.getString("expectedBaseline"))
                .put(request.optString("reservationId")).put(format.getBoolean("bom")).put(format.getString("newline")).toString().getBytes(StandardCharsets.UTF_8));
    }

    private String writeKey(JSONObject request) throws Exception {
        return "write-" + sessionId + "-" + MarkdownCodec.digest(request.getString("operationId").getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject error(String kind, Exception error) throws Exception {
        String code = error instanceof SecurityException ? "permission" : error.getMessage();
        if (code == null || !code.matches("permission|readonly|offline|unavailable|deleted|identity-changed|encoding|size|space|io|protocol")) code = "io";
        return new JSONObject().put("kind", kind).put("error", new JSONObject().put("code", code).put("recoverable", !code.matches("encoding|size|protocol")));
    }

    private JSONObject receipt(String baseline) throws Exception {
        return new JSONObject().put("kind", "committed").put("baseline", baseline).put("durability", "provider");
    }

    private JSONObject journal(JSONObject request, MarkdownFiles.Target selected, String phase, byte[] bytes) throws Exception {
        JSONObject result = new JSONObject().put("phase", phase).put("sessionId", sessionId).put("operationId", request.getString("operationId"))
                .put("identity", selected.identity).put("uri", selected.uri.toString()).put("request", request)
                .put("expectedDigest", MarkdownCodec.digest(bytes)).put("signature", signature(request)).put("sequence", operationSequence(request)).put("candidate", request.has("reservationId"));
        if (!"pending".equals(phase)) result.remove("request");
        return result;
    }

    public Object handle(String method, JSONObject request) throws Exception {
        if (closed) throw new IOException("protocol");
        switch (method) {
            case "read": {
                try {
                    MarkdownFiles.Target selected = requestTarget(request);
                    if (transferTarget != null) throw new IOException("unavailable");
                    if (selected != target) throw new IOException("protocol");
                    MarkdownCodec.Decoded read = files.read(selected);
                    // Return snapshot metadata without changing the host's current baseline.
                    // A stale frontend reload must not acknowledge the newer file version.
                    JSONObject snapshot = selected.json().put("baseline", read.baseline)
                            .put("format", new JSONObject().put("bom", read.bom).put("newline", read.newline));
                    return new JSONObject().put("kind", "read").put("content", read.content).put("target", snapshot);
                } catch (Exception failure) { return error("blocked", failure); }
            }
            case "write": return write(request, false);
            case "reconcileWrite": return write(request, true);
            case "reserveSaveAs": {
                try {
                    if (!sessionId.equals(request.getString("sessionId"))) throw new IOException("protocol");
                    operationSequence(request);
                    MarkdownFiles.Target selected = targets.get(request.getJSONObject("target").getString("handle"));
                    if (selected == null || selected == target) throw new IOException("protocol");
                    String owner = registry.owner(selected.identity);
                    if (owner != null && !owner.equals(sessionId)) return new JSONObject().put("kind", "occupied").put("sessionId", owner);
                    MarkdownRegistry.Claim claim = registry.claim(selected.identity, sessionId, true);
                    registry.reserveOperation(selected.identity, sessionId, request.getString("operationId"));
                    return new JSONObject().put("kind", "reserved").put("reservation", new JSONObject().put("id", claim.reservation).put("target", selected.json()));
                } catch (Exception failure) { return error("blocked", failure); }
            }
            case "prepareSaveAs": {
                JSONObject operation = request.getJSONObject("operation");
                MarkdownFiles.Target selected = requestTarget(operation);
                registry.require(selected.identity, sessionId, operation.getString("reservationId"));
                String previousHandle = request.isNull("previousTarget") ? "" : request.getJSONObject("previousTarget").getString("handle");
                if (!previousHandle.equals(target == null ? "" : target.handle)) throw new IOException("protocol");
                putDraft(operation, target, generation);
                store.put("switch-" + sessionId, generation, operation.getLong("revision"),
                        new JSONObject().put("operation", operation).put("candidateUri", selected.uri).put("previousUri", target == null ? JSONObject.NULL : target.uri).toString());
                prepared.put(operation.getString("operationId"), signature(operation));
                return JSONObject.NULL;
            }
            case "commitSaveAs": {
                try {
                    JSONObject operation = request.getJSONObject("operation");
                    long nextGeneration = request.getLong("nextTargetGeneration");
                    // Repeated commits are idempotent, even after their first acknowledgement was lost.
                    if (nextGeneration == generation && operation.getString("operationId").equals(committedOperation) && signature(operation).equals(committedSignature)) {
                        return new JSONObject().put("kind", "committed").put("target", target.json());
                    }
                    MarkdownFiles.Target selected = requestTarget(operation);
                    if (nextGeneration != generation + 1 || nextGeneration != operation.getLong("targetGeneration")) throw new IOException("protocol");
                    String previousHandle = request.isNull("previousTarget") ? "" : request.getJSONObject("previousTarget").getString("handle");
                    if (!previousHandle.equals(target == null ? "" : target.handle)) throw new IOException("protocol");
                    MarkdownStore.Record record = store.read(writeKey(operation));
                    JSONObject committed = record == null ? new JSONObject() : new JSONObject(record.payload);
                    if (!("committed".equals(committed.optString("phase")) || "bound".equals(committed.optString("phase"))) || !signature(operation).equals(committed.optString("signature"))) throw new IOException("unavailable");
                    String baseline = committed.getString("expectedDigest");
                    if (!baseline.equals(request.getJSONObject("receipt").getString("baseline"))) throw new IOException("protocol");
                    selected.baseline = baseline;
                    transferTarget = selected; transferGeneration = nextGeneration;
                    putDraft(operation, selected, nextGeneration);
                    store.put("binding-" + sessionId, nextGeneration, operation.getLong("revision"), new JSONObject().put("uri", selected.uri).put("identity", selected.identity).put("operationId", operation.getString("operationId")).toString());
                    store.put(writeKey(operation), nextGeneration, operation.getLong("revision"), committed.put("phase", "bound").toString());
                    registry.bind(selected.identity, sessionId, operation.getString("operationId"));
                    if (target != null && !target.identity.equals(selected.identity)) { registry.release(target.identity, sessionId); targets.remove(target.handle); }
                    target = selected; generation = nextGeneration; resourceTree = null;
                    transferTarget = null;
                    committedOperation = operation.getString("operationId"); committedSignature = signature(operation); committedRevision = operation.getLong("revision");
                    prepared.remove(committedOperation);
                    store.remove("switch-" + sessionId);
                    pruneReceipts();
                    return new JSONObject().put("kind", "committed").put("target", selected.json());
                } catch (Exception failure) { return error("indeterminate", failure); }
            }
            case "releaseSaveAs": {
                if (!sessionId.equals(request.getString("sessionId"))) throw new IOException("protocol");
                for (MarkdownFiles.Target selected : targets.values()) {
                    if (selected == target) continue;
                    if (!sessionId.equals(registry.owner(selected.identity))) continue;
                    MarkdownRegistry.Claim claim = registry.require(selected.identity, sessionId, request.getString("reservationId"));
                    if (claim.unresolved) throw new IOException("unavailable");
                    registry.release(selected.identity, sessionId);
                    targets.remove(selected.handle); prepared.remove(claim.operation);
                    store.remove("switch-" + sessionId);
                    break;
                }
                return JSONObject.NULL;
            }
            case "putDraft": {
                if (!sessionId.equals(request.getString("sessionId"))) throw new IOException("protocol");
                MarkdownCodec.encodeDraft(request.getString("content"));
                long requestGeneration = request.getLong("targetGeneration");
                if (requestGeneration == generation && transferTarget != null) {
                    JSONObject copy = new JSONObject(request.toString()).put("targetGeneration", transferGeneration).put("target", transferTarget.json()).put("format", transferTarget.json().getJSONObject("format"));
                    putDraft(copy, transferTarget, transferGeneration);
                    return JSONObject.NULL;
                }
                if (requestGeneration != generation) {
                    if (requestGeneration != generation - 1 || committedOperation == null) throw new IOException("protocol");
                    if (request.getLong("revision") <= committedRevision) return JSONObject.NULL;
                    request = new JSONObject(request.toString()).put("targetGeneration", generation).put("target", target.json()).put("format", target.json().getJSONObject("format"));
                }
                putDraft(request, target, generation); return JSONObject.NULL;
            }
            case "clearDraft": {
                long requestGeneration = request.getLong("targetGeneration");
                if (!sessionId.equals(request.getString("sessionId")) || requestGeneration < 0 || requestGeneration > generation) throw new IOException("protocol");
                store.put("draft-" + sessionId, requestGeneration, request.getLong("revision"), ""); return JSONObject.NULL;
            }
            case "releaseSession": {
                if (!sessionId.equals(request.getString("sessionId")) || request.getLong("targetGeneration") != generation) throw new IOException("protocol");
                registry.detach(sessionId); closed = true; cleanupClosedSession(sessionId); return JSONObject.NULL;
            }
            default: throw new IOException("protocol");
        }
    }

    private Object write(JSONObject request, boolean reconcile) throws Exception {
        MarkdownFiles.Target selected;
        byte[] bytes;
        boolean journaled = false;
        try {
            selected = requestTarget(request);
            if (transferTarget != null && selected != transferTarget) throw new IOException("unavailable");
            if (!selected.writable) throw new IOException("readonly");
            registry.require(selected.identity, sessionId, request.optString("reservationId"));
            JSONObject format = request.getJSONObject("target").getJSONObject("format");
            bytes = MarkdownCodec.encode(request.getString("content"), format.getBoolean("bom"), format.getString("newline"));
            if (request.getLong("revision") < 0 || request.getString("operationId").length() > 256) throw new IOException("protocol");
            MarkdownStore.Record lastWrite = store.read(writeKey(request));
            if (request.has("reservationId") && !signature(request).equals(prepared.get(request.getString("operationId")))) throw new IOException("protocol");
            if (lastWrite != null && !lastWrite.payload.isEmpty()) {
                JSONObject previous = new JSONObject(lastWrite.payload);
                if (!signature(request).equals(previous.getString("signature"))) throw new IOException("protocol");
                if ("committed".equals(previous.getString("phase")) || "bound".equals(previous.getString("phase"))) return receipt(previous.getString("expectedDigest"));
                if (!reconcile && "pending".equals(previous.getString("phase"))) return error("indeterminate", new IOException("unavailable"));
                if ("cancelled".equals(previous.getString("phase"))) return new JSONObject().put("kind", "conflict").put("baseline", files.read(selected).baseline);
            }
            if (reconcile) {
                MarkdownStore.Record record = lastWrite;
                JSONObject prior = record == null || record.payload.isEmpty() ? new JSONObject() : new JSONObject(record.payload);
                if (!signature(request).equals(prior.optString("signature"))) throw new IOException("protocol");
                String baseline = files.read(selected).baseline;
                if (!baseline.equals(prior.getString("expectedDigest"))) return error("indeterminate", new IOException("io"));
                store.put(writeKey(request), request.getLong("targetGeneration"), request.getLong("revision"), journal(request, selected, "committed", bytes).toString());
                registry.written(selected.identity, sessionId, request.getString("operationId"));
                selected.baseline = baseline;
                selected.bom = format.getBoolean("bom"); selected.newline = format.getString("newline");
                pruneReceipts();
                return receipt(baseline);
            }
            MarkdownRegistry.Claim claim = registry.require(selected.identity, sessionId, request.optString("reservationId"));
            if (claim.unresolved) return error("indeterminate", new IOException("unavailable"));
            if (request.getLong("revision") < lastWriteRevision) throw new IOException("protocol");
            long sequence = operationSequence(request);
            if (sequence <= lastWriteSequence) throw new IOException("protocol");
            String baseline = files.read(selected).baseline;
            if (!baseline.equals(request.getString("expectedBaseline"))) return new JSONObject().put("kind", "conflict").put("baseline", baseline);
            // Persist the exact immutable operation before opening a truncating descriptor.
            if (!request.has("reservationId")) putDraft(request, target, generation);
            store.put(writeKey(request), request.getLong("targetGeneration"), request.getLong("revision"), journal(request, selected, "pending", bytes).toString());
            journaled = true;
            registry.begin(selected.identity, sessionId, request.optString("reservationId"), request.getString("operationId"));
            lastWriteRevision = request.getLong("revision");
            lastWriteSequence = sequence;
            store.put("watermark-" + sessionId, 0, sequence, new JSONObject().put("instanceId", instanceId).put("sequence", sequence).toString());
        } catch (Exception failure) { return error(reconcile || journaled ? "indeterminate" : "blocked", failure); }
        try {
            // The provider has no CAS: this final check narrows, but cannot eliminate,
            // the race with an external writer between reading and truncating.
            String current = files.read(selected).baseline;
            if (!current.equals(request.getString("expectedBaseline"))) {
                store.put(writeKey(request), request.getLong("targetGeneration"), request.getLong("revision"), journal(request, selected, "cancelled", bytes).toString());
                registry.resolved(selected.identity, sessionId, request.getString("operationId"));
                pruneReceipts();
                return new JSONObject().put("kind", "conflict").put("baseline", current);
            }
            files.write(selected, bytes);
            String baseline = MarkdownCodec.digest(bytes);
            store.put(writeKey(request), request.getLong("targetGeneration"), request.getLong("revision"), journal(request, selected, "committed", bytes).toString());
            registry.written(selected.identity, sessionId, request.getString("operationId"));
            selected.baseline = baseline;
            JSONObject format = request.getJSONObject("target").getJSONObject("format");
            selected.bom = format.getBoolean("bom"); selected.newline = format.getString("newline");
            pruneReceipts();
            return receipt(baseline);
        } catch (Exception failure) { return error("indeterminate", failure); }
    }

    private void putDraft(JSONObject request, MarkdownFiles.Target selected, long targetGeneration) throws Exception {
        MarkdownCodec.encodeDraft(request.getString("content"));
        JSONObject payload = new JSONObject().put("draft", request).put("updatedAt", System.currentTimeMillis())
                .put("name", selected == null ? "Untitled.md" : selected.name)
                .put("uri", selected == null ? JSONObject.NULL : selected.uri.toString());
        store.put("draft-" + sessionId, targetGeneration, request.getLong("revision"), payload.toString());
        if (request.getLong("revision") >= revision) { revision = request.getLong("revision"); content = request.getString("content"); }
    }

    private static void validateFonts(JSONObject fonts) throws Exception {
        if (fonts.length() != 3) throw new IOException("protocol");
        for (String group : new String[]{"global", "editor", "code"}) {
            JSONArray items = fonts.getJSONArray(group);
            if (items.length() > 64) throw new IOException("protocol");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String family = item.getString("family");
                double weight = item.getDouble("weight");
                if (item.length() != 2 || family.trim().isEmpty() || family.length() > 256 ||
                    weight != Math.floor(weight) || weight < 1 || weight > 1000) throw new IOException("protocol");
            }
        }
    }

    public JSONObject preferences(JSONObject patch) throws Exception {
        MarkdownStore.Record record = preferencesStore.read("preferences");
        JSONObject value = record == null ? new JSONObject().put("mode", "ir").put("theme", "system").put("fontSize", 16) : new JSONObject(record.payload);
        if (patch != null) {
            if (patch.has("mode")) { String mode = patch.getString("mode"); if (!mode.matches("ir|wysiwyg|sv")) throw new IOException("protocol"); value.put("mode", mode); }
            if (patch.has("theme")) { String theme = patch.getString("theme"); if (!theme.matches("system|light|dark")) throw new IOException("protocol"); value.put("theme", theme); }
            if (patch.has("fontSize")) { int size = patch.getInt("fontSize"); if (size < 9 || size > 72) throw new IOException("protocol"); value.put("fontSize", size); }
            if (patch.has("fonts")) { validateFonts(patch.getJSONObject("fonts")); value.put("fonts", patch.getJSONObject("fonts")); }
            preferencesStore.put("preferences", 0, record == null ? 0 : record.revision + 1, value.toString());
        }
        return value;
    }

    public JSONArray recoveries() throws Exception {
        JSONArray result = new JSONArray();
        for (String key : store.keys("draft-")) {
            if (registry.isSessionActive(key.substring(6))) continue;
            try {
                MarkdownStore.Record record = store.read(key);
                if (record == null || record.payload.isEmpty()) continue;
                JSONObject payload = new JSONObject(record.payload);
                result.put(new JSONObject().put("id", key).put("displayName", payload.getString("name")).put("updatedAt", payload.getLong("updatedAt")));
            } catch (Exception failure) {
                // Corrupt records remain visible, but opening or discarding them cannot
                // silently replace the unreadable bytes with an empty draft.
                result.put(new JSONObject().put("id", key).put("displayName", "markdownCorruptRecovery").put("updatedAt", 0));
            }
        }
        if (store.hasUnrecoveredPending()) result.put(new JSONObject().put("id", "unreadable-pending").put("displayName", "markdownCorruptRecovery").put("updatedAt", 0));
        return result;
    }

    public void restore(String key) throws Exception {
        if (!key.startsWith("draft-") || registry.isSessionActive(key.substring(6))) throw new IOException("protocol");
        MarkdownStore.Record record = store.read(key);
        if (record == null || record.payload.isEmpty()) throw new IOException("unavailable");
        JSONObject payload = new JSONObject(record.payload), draft = payload.getJSONObject("draft");
        MarkdownCodec.encodeDraft(draft.getString("content"));
        content = draft.getString("content"); revision = Math.max(1, record.revision); recovered = true;
        String previousSession = key.substring(6), restoredUri = payload.optString("uri", "");
        for (String writeKey : store.keys("write-" + previousSession + "-")) {
            MarkdownStore.Record writeRecord = store.read(writeKey);
            if (writeRecord == null || writeRecord.payload.isEmpty()) continue;
            JSONObject operation = new JSONObject(writeRecord.payload);
            if (!"pending".equals(operation.optString("phase")) && !("committed".equals(operation.optString("phase")) && operation.optBoolean("candidate"))) continue;
            try {
                MarkdownFiles.Target checked = files.reopen(operation.getString("uri"));
                if (!checked.baseline.equals(operation.getString("expectedDigest"))) continue;
                String operationId = operation.getString("operationId");
                if (registry.owner(checked.identity) != null) {
                    registry.written(checked.identity, previousSession, operationId);
                    if (operation.optBoolean("candidate")) {
                        store.put("binding-" + previousSession, writeRecord.generation, writeRecord.revision,
                                new JSONObject().put("uri", checked.uri).put("identity", checked.identity).put("operationId", operationId).toString());
                        registry.bind(checked.identity, previousSession, operationId);
                    }
                    registry.takeRecovered(checked.identity, previousSession, sessionId);
                }
                operation.put("phase", "recovered").remove("request");
                store.put(writeKey, writeRecord.generation, writeRecord.revision, operation.toString());
                if (operation.optBoolean("candidate")) restoredUri = checked.uri.toString();
            } catch (Exception ignored) { /* Keep an uncertain target reserved; recovery can still be a copy. */ }
        }
        if (!restoredUri.isEmpty() && !restoredUri.equals("null")) {
            try {
                MarkdownFiles.Target checked = files.reopen(restoredUri);
                if (registry.owner(checked.identity) != null && !sessionId.equals(registry.owner(checked.identity))) registry.takeRecovered(checked.identity, previousSession, sessionId);
                registry.claim(checked.identity, sessionId, false);
                target = checked; targets.put(checked.handle, checked);
            } catch (Exception ignored) { target = null; }
            content = draft.getString("content");
        }
        JSONObject copy = new JSONObject(draft.toString()).put("sessionId", sessionId).put("targetGeneration", 0).put("revision", revision);
        if (target != null) copy.put("target", target.json()); else copy.remove("target");
        putDraft(copy, target, 0);
        if (!registry.hasProtectedClaims(previousSession)) {
            registry.detach(previousSession);
            cleanupClosedSession(previousSession);
        }
        // The original record stays until the user explicitly discards it.
    }

    public void discard(String key) throws Exception {
        if (!key.startsWith("draft-") || registry.isSessionActive(key.substring(6))) throw new IOException("protocol");
        if (registry.hasProtectedClaims(key.substring(6))) throw new IOException("unavailable");
        MarkdownStore.Record record = store.read(key);
        if (record == null) return;
        store.put(key, record.generation, record.revision, "");
        store.remove(key);
        cleanupClosedSession(key.substring(6));
    }
}
