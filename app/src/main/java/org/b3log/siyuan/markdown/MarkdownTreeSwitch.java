package org.b3log.siyuan.markdown;

import android.net.Uri;
import org.json.JSONObject;
import java.io.IOException;

/** One prepared, occupied document per container. Commit performs no provider I/O. */
public final class MarkdownTreeSwitch {
    public interface Factory { MarkdownHost create() throws Exception; }
    private final Factory factory;
    private final MarkdownRegistry registry;
    private MarkdownHost previous, candidate, committed;
    private String ticket, committedTicket;
    private JSONObject bootstrap, committedBootstrap;
    public MarkdownTreeSwitch(Factory factory, MarkdownRegistry registry) { this.factory = factory; this.registry = registry; }
    public boolean pending() { return candidate != null; }
    public JSONObject prepare(MarkdownHost current, Uri uri, JSONObject languages, String language, boolean dark) throws Exception {
        if (candidate != null || current.closed) throw new IOException("unavailable");
        MarkdownFiles.Target selected = current.files.open(uri, 0);
        if (current.target != null && current.target.identity.equals(selected.identity)) return new JSONObject().put("kind", "current");
        if (registry.owner(selected.identity) != null) return new JSONObject().put("kind", "occupied");
        MarkdownHost next = factory.create();
        try {
            next.openTarget(selected);
            JSONObject initial = next.bootstrap(languages, language, dark);
            String nextTicket = MarkdownIds.newId();
            if (nextTicket.equals(committedTicket)) throw new IOException("identifier collision");
            previous = current; candidate = next; bootstrap = initial; ticket = nextTicket;
            return new JSONObject().put("kind", "ready").put("ticket", ticket).put("sessionId", next.sessionId);
        } catch (Exception error) { next.discardPrepared(); throw error; }
    }
    public MarkdownHost commit(String requested, MarkdownHost current) throws Exception {
        if (requested.equals(committedTicket) && current == committed) return current;
        if (candidate == null || !requested.equals(ticket) || previous != current || !previous.closed) throw new IOException("protocol");
        committed = candidate; committedTicket = ticket; committedBootstrap = bootstrap;
        candidate = null; previous = null; ticket = null; bootstrap = null;
        return committed;
    }
    public JSONObject committedBootstrap(String requested, MarkdownHost current) throws Exception {
        if (!requested.equals(committedTicket) || current != committed) throw new IOException("protocol");
        return new JSONObject(committedBootstrap.toString());
    }
    public void cancel(String requested) throws Exception {
        if (candidate == null) return;
        if (!ticket.equals(requested)) throw new IOException("protocol");
        if (previous.closed) throw new IOException("unavailable");
        drain();
    }
    public void drain() throws Exception {
        if (candidate != null) candidate.discardPrepared();
        candidate = null; previous = null; ticket = null; bootstrap = null;
    }
}
