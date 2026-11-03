package org.b3log.siyuan;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.JavaScriptReplyProxy;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.b3log.siyuan.markdown.MarkdownCodec;
import org.b3log.siyuan.markdown.MarkdownTheme;
import org.b3log.siyuan.markdown.MarkdownIds;
import org.b3log.siyuan.markdown.MarkdownFiles;
import org.b3log.siyuan.markdown.MarkdownHost;
import org.b3log.siyuan.markdown.MarkdownRegistry;
import org.b3log.siyuan.markdown.MarkdownRuntime;
import org.b3log.siyuan.markdown.MarkdownRouting;
import org.b3log.siyuan.markdown.MarkdownImages;
import org.b3log.siyuan.markdown.MarkdownImageImport;
import org.b3log.siyuan.markdown.MarkdownTree;
import org.b3log.siyuan.markdown.MarkdownTreeFiles;
import org.b3log.siyuan.markdown.MarkdownTreeView;
import org.b3log.siyuan.markdown.MarkdownTreeSwitch;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A single-document container, isolated from MainActivity and the kernel. */
public final class MarkdownActivity extends AppCompatActivity {
    private static final String ORIGIN = "https://siyuan-markdown.local";
    private static final String PAGE = ORIGIN + "/stage/build/markdown/index.html";
    // This queue outlives an Activity callback and serializes every provider write.
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final ExecutorService treeIO = Executors.newSingleThreadExecutor();
    private WebView web;
    private TextView loading;
    private volatile MarkdownHost host;
    private MarkdownTree tree;
    private MarkdownTreeFiles treeFiles;
    private MarkdownTreeSwitch treeSwitch;
    private volatile boolean treePreparing;
    private volatile long switchEpoch;
    private long pickerSwitchEpoch;
    private File runtime;
    private JSONObject languages = new JSONObject();
    private String language = "en";
    private volatile String preferredTheme = "system";
    private JavaScriptReplyProxy replyProxy;
    private final Map<String, Runnable> commands = new ConcurrentHashMap<>();
    private final Map<String, Resource> resources = new ConcurrentHashMap<>();
    private final Map<String, String> resourceReferences = new ConcurrentHashMap<>();
    private final Map<String, ReplyToken> replies = new ConcurrentHashMap<>();
    private volatile long pageEpoch;
    private String pickerAction;
    private String pickerRequest;
    private JSONObject pickerFormat;
    private boolean closing;
    private volatile boolean destroyed;
    private Uri imageSource;
    private Uri imageDirectory;
    private String imageName;
    private final String activityLease = MarkdownIds.newId();
    private boolean activityLeaseAttached;
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        String action = pickerAction, request = pickerRequest;
        JSONObject format = pickerFormat;
        pickerAction = null; pickerRequest = null; pickerFormat = null;
        if (action == null) return;
        if (destroyed) return;
        Intent data = result.getData();
        Uri uri = result.getResultCode() == RESULT_OK && data != null ? data.getData() : null;
        if (uri == null) {
            imageSource = null; imageDirectory = null; imageName = null;
            reply(request, action.equals("resources") ? false : object("kind", "cancelled"), null);
            return;
        }
        int flags = data.getFlags();
        if (action.equals("tree-root")) {
            long epoch = pickerSwitchEpoch;
            treeIO.execute(() -> {
                try {
                    if (destroyed || epoch != switchEpoch || treePreparing) throw new IOException("unavailable");
                    MarkdownTree.Root selected = treeFiles.choose(uri, flags);
                    if (destroyed || epoch != switchEpoch || treePreparing) throw new IOException("unavailable");
                    tree.select(selected);
                    tree.current(treeFiles.localKey(tree.root(), host.target));
                    reply(request, tree.state(), null);
                    runOnUiThread(() -> command("tree-change", null));
                } catch (Exception error) { reply(request, MarkdownTree.blocked(error), null); }
            });
            return;
        }
        if (action.equals("image") || action.equals("image-directory")) { pickerAction = "image-preparing"; pickerRequest = request; }
        IO.execute(() -> {
            try {
                if (action.equals("open")) {
                    runOnUiThread(() -> switchDocument(uri, flags, null));
                    reply(request, JSONObject.NULL, null);
                } else if (action.equals("save-as")) {
                    try { reply(request, object("kind", "selected").put("target", host.candidate(uri, flags, format).json()), null); }
                    catch (Exception error) { reply(request, object("kind", "occupied".equals(error.getMessage()) ? "occupied" : "blocked"), null); }
                } else if (action.equals("resources")) {
                    boolean allowed = host.target != null && host.files.isParent(uri, host.target);
                    if (allowed) { host.files.persist(uri, flags); host.resourceTree = uri; resources.clear(); resourceReferences.clear(); }
                    reply(request, allowed, null);
                } else if (action.equals("image")) {
                    String name = imageName(uri);
                    if (host.target == null || !MarkdownImageImport.validName(name)) { finishImage(request, object("kind", "blocked")); return; }
                    runOnUiThread(() -> beginImageDestination(request, uri, name));
                } else if (action.equals("image-directory")) {
                    if (host.target == null || !host.files.isParent(uri, host.target)) { finishImage(request, object("kind", "blocked")); return; }
                    runOnUiThread(() -> nameImage(request, uri));
                }
            } catch (Exception error) {
                if (action.equals("image") || action.equals("image-directory")) finishImage(request, object("kind", "blocked"));
                else reply(request, null, error);
            }
        });
    });

    private static final class Resource {
        final Uri uri;
        final String mime;
        final String session;
        final long generation;
        final Uri tree;
        Resource(Uri uri, String mime, MarkdownHost host, boolean exactFile) {
            this.uri = uri; this.mime = mime; this.session = host.sessionId; this.generation = host.generation; this.tree = exactFile ? null : host.resourceTree;
        }
        boolean allowed(MarkdownHost host) {
            return host != null && !host.closed && session.equals(host.sessionId) && generation == host.generation && (tree == null || tree.equals(host.resourceTree));
        }
    }

    private static final class ReplyToken {
        final String id;
        final long epoch;
        final JavaScriptReplyProxy proxy;
        volatile long documentEpoch;
        boolean viewOnly;
        ReplyToken(String id, long epoch, long documentEpoch, JavaScriptReplyProxy proxy) { this.id = id; this.epoch = epoch; this.documentEpoch = documentEpoch; this.proxy = proxy; }
    }

    public static boolean isMarkdownIntent(android.content.Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null || !"content".equals(intent.getData().getScheme())) return false;
        String name = null;
        try (Cursor cursor = context.getContentResolver().query(intent.getData(), new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) { /* A declared Markdown MIME still reaches the permission error UI. */ }
        return MarkdownRouting.matches(intent.getAction(), intent.getData().getScheme(), intent.getType(), name);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SystemBars.configure(this, !dark());
        loading = new TextView(this); loading.setText("SiYuan"); setContentView(loading); SystemBars.fitContent(this); updateSystemBars();
        try {
            MarkdownRegistry.INSTANCE.attach(activityLease);
            activityLeaseAttached = true;
        } catch (IOException error) {
            showFailure();
            return;
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { closeEditor(); }
        });
        IO.execute(() -> {
            try {
                runtime = MarkdownRuntime.prepare(this);
                if (destroyed) return;
                loadLanguages();
                host = new MarkdownHost(this);
                treeFiles = new MarkdownTreeFiles(this);
                tree = new MarkdownTree(treeFiles, new MarkdownTreeView(new File(getNoBackupFilesDir(), "markdown-v1/tree-view.json")));
                treeSwitch = new MarkdownTreeSwitch(() -> new MarkdownHost(this), MarkdownRegistry.INSTANCE);
                preferredTheme = host.preferences(null).optString("theme", "system");
                runOnUiThread(() -> { if (!destroyed) updateSystemBars(); });
                if (isMarkdownIntent(this, getIntent())) host.open(getIntent().getData(), getIntent().getFlags());
                updateTreeTarget(true);
                if (!destroyed) runOnUiThread(this::loadEditor);
            } catch (Exception error) { showFailure(); }
        });
    }

    private void loadLanguages() throws Exception {
        String tag = getResources().getConfiguration().getLocales().get(0).toLanguageTag();
        if (tag.toLowerCase(Locale.ROOT).startsWith("zh")) language = tag.matches("(?i).*(TW|HK|MO|Hant).*") ? "zh-TW" : "zh-CN";
        else if (tag.startsWith("pt")) language = "pt-BR";
        else language = tag.split("-")[0];
        if (!language.matches("[A-Za-z-]+") || !new File(runtime, "appearance/langs/" + language + ".json").isFile()) language = "en";
        languages = new JSONObject(new String(Files.readAllBytes(new File(runtime, "appearance/langs/" + language + ".json").toPath()), StandardCharsets.UTF_8));
    }

    @SuppressWarnings("deprecation")
    private void loadEditor() {
        if (isFinishing() || isDestroyed()) return;
        if (!App.isMarkdownWebViewReady() || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) { showFailure(); return; }
        web = new WebView(this);
        web.setBackgroundColor(surfaceColor());
        WebSettings settings = web.getSettings();
        // The standalone adapter also masks the localStorage API before Vditor starts.
        // Content and preferences have a single native persistence authority.
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false); settings.setAllowUniversalAccessFromFileURLs(false);
        // 网络子资源由页面 CSP 限定为图片，HTTP 图片与 HTTPS 图片使用相同的预览入口。
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder().setDomain("siyuan-markdown.local")
                .addPathHandler("/", new WebViewAssetLoader.InternalStoragePathHandler(this, runtime)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return !request.isForMainFrame() || !PAGE.equals(request.getUrl().toString()); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"GET".equals(request.getMethod())) return denied();
                if (!trusted(uri)) {
                    if (!request.isForMainFrame() && ("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) &&
                            uri.getUserInfo() == null && !"siyuan-markdown.local".equals(uri.getHost())) return null;
                    return denied();
                }
                if (uri.getPath() != null && uri.getPath().startsWith("/resource/")) {
                    Resource resource = resources.get(uri.toString());
                    MarkdownHost current = host;
                    if (resource == null || !resource.allowed(current)) return denied();
                    try {
                        byte[] data;
                        try (InputStream input = getContentResolver().openInputStream(resource.uri)) { data = MarkdownCodec.read(input, 32 * 1024 * 1024); }
                        if (!MarkdownImages.matches(data, resource.mime) || current != host || !resource.allowed(current)) return denied();
                        return response(resource.mime, new ByteArrayInputStream(data));
                    } catch (Exception error) { return denied(); }
                }
                if (uri.getPath() == null || !uri.getPath().startsWith("/stage/build/markdown/")) return denied();
                // Only this bundled script aliases outside the editor's asset root.
                if ("/stage/build/markdown/vditor/dist/js/lute/lute.min.js".equals(uri.getEncodedPath())) {
                    uri = uri.buildUpon().path("/stage/protyle/js/lute/lute.min.js").build();
                }
                WebResourceResponse result = loader.shouldInterceptRequest(uri);
                if (result == null) return denied();
                if (PAGE.equals(uri.toString())) {
                    try (InputStream input = result.getData()) {
                        String html = new String(MarkdownCodec.read(input, 1024 * 1024), StandardCharsets.UTF_8);
                        result.setData(new ByteArrayInputStream(MarkdownTheme.page(html, effectiveDark()).getBytes(StandardCharsets.UTF_8)));
                    } catch (Exception error) { return denied(); }
                }
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Content-Type-Options", "nosniff");
                headers.put("Cache-Control", "no-store");
                headers.put("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: http: https:; font-src 'self'; connect-src 'none'; object-src 'none'; frame-src 'none'; base-uri 'none'; form-action 'none'");
                result.setResponseHeaders(headers);
                return result;
            }
            @Override public void onPageFinished(WebView view, String url) { if (!PAGE.equals(url)) view.stopLoading(); }
            @Override public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                resources.clear(); replies.clear(); pageEpoch++; replyProxy = null; showFailure(); return true;
            }
        });
        WebViewCompat.addWebMessageListener(web, "siyuanMarkdownNative", Collections.singleton(ORIGIN), (view, message, sourceOrigin, isMainFrame, proxy) -> {
            if (view != web || !isMainFrame || !trusted(sourceOrigin) || !PAGE.equals(web.getUrl())) return;
            String input = message.getData();
            if (input == null || input.length() > MarkdownCodec.MAX_DRAFT_BYTES * 6 + 65536) return;
            try {
                JSONObject envelope = new JSONObject(input);
                String id = envelope.getString("id"), action = envelope.getString("action");
                if (id.length() > 256 || action.length() > 80 || replies.size() >= 512) return;
                if (action.equals("bootstrap")) replyProxy = proxy;
                String key = pageEpoch + ":" + id;
                if (replies.putIfAbsent(key, new ReplyToken(id, pageEpoch, switchEpoch, proxy)) != null) return;
                dispatch(key, action, envelope.opt("payload"));
            } catch (Exception ignored) { /* An invalid page message gains no authority. */ }
        });
        FrameLayout frame = new FrameLayout(this);
        frame.addView(web, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(frame); SystemBars.fitContent(this); updateSystemBars(); loadPage();
    }

    private void loadPage() {
        pageEpoch++; replies.clear(); commands.clear(); replyProxy = null;
        if (web != null) web.loadUrl(PAGE);
    }

    private static boolean trusted(Uri uri) { return "https".equals(uri.getScheme()) && "siyuan-markdown.local".equals(uri.getHost()) && (uri.getPort() == -1 || uri.getPort() == 443); }
    private WebResourceResponse denied() { return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0])); }
    private WebResourceResponse response(String mime, InputStream data) {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Content-Type-Options", "nosniff"); headers.put("Cache-Control", "no-store");
        return new WebResourceResponse(mime, null, 200, "OK", headers, data);
    }
    private static JSONObject object(String key, Object value) { try { return new JSONObject().put(key, value); } catch (Exception error) { throw new IllegalStateException(error); } }
    private boolean dark() { return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES; }
    private boolean effectiveDark() { return MarkdownTheme.dark(preferredTheme, dark()); }
    private int surfaceColor() { return effectiveDark() ? Color.rgb(30, 30, 30) : Color.WHITE; }
    private void updateSystemBars() {
        SystemBars.setAppearance(this, !effectiveDark());
        int background = surfaceColor();
        getWindow().setBackgroundDrawable(new ColorDrawable(background));
        findViewById(android.R.id.content).setBackgroundColor(background);
        if (loading != null) { loading.setBackgroundColor(background); loading.setTextColor(effectiveDark() ? Color.WHITE : Color.BLACK); }
        if (web != null) web.setBackgroundColor(background);
    }
    private String label(String key) {
        if (languages.has(key)) return languages.optString(key);
        // Startup failures can precede extraction of the shared language bundle.
        if (key.equals("markdownEditor")) return getString(R.string.markdown_editor);
        if (key.equals("markdownLoadError")) return getString(R.string.markdown_load_error);
        if (key.equals("close")) return getString(R.string.markdown_close);
        return key;
    }

    private void dispatch(String id, String action, Object payload) {
        if (action.equals("response")) {
            try {
                JSONObject value = (JSONObject) payload;
                Runnable continuation = commands.remove(value.getString("id"));
                if (continuation != null) {
                    if (value.getBoolean("accepted")) continuation.run(); else closing = false;
                }
                reply(id, JSONObject.NULL, null);
            } catch (Exception error) { reply(id, null, error); }
            return;
        }
        if (action.startsWith("tree-")) { dispatchTree(id, action, payload); return; }
        if (action.equals("open") || action.equals("save-as") || action.equals("authorize-resources") || action.equals("insert-image")) {
            if (pickerAction != null) { reply(id, null, new IOException("unavailable")); return; }
            if (action.equals("insert-image") && (host == null || host.target == null)) { reply(id, object("kind", "blocked"), null); return; }
            pickerRequest = id; pickerFormat = payload instanceof JSONObject ? (JSONObject) payload : null;
            Intent intent;
            if (action.equals("authorize-resources")) { pickerAction = "resources"; intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); }
            else if (action.equals("save-as")) {
                pickerAction = "save-as"; intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/markdown").addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, host.target == null ? "Untitled.md" : host.target.name);
            } else {
                pickerAction = action.equals("insert-image") ? "image" : "open";
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(pickerAction.equals("image") ? "image/*" : "*/*").addCategory(Intent.CATEGORY_OPENABLE);
                if (pickerAction.equals("open")) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/markdown", "text/x-markdown", "text/plain", "application/octet-stream"});
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { picker.launch(intent); } catch (Exception error) { pickerAction = null; reply(id, null, error); }
            return;
        }
        if (action.equals("confirm") || action.equals("confirm-close")) {
            AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(label("markdownEditor"));
            if (action.equals("confirm-close")) {
                dialog.setMessage(label("markdownCloseDraft")).setPositiveButton(label("markdownKeepDraft"), (d, w) -> reply(id, "keep", null))
                        .setNeutralButton(label("saveAs"), (d, w) -> reply(id, "save-as", null)).setNegativeButton(label("cancel"), (d, w) -> reply(id, "cancel", null))
                        .setOnCancelListener(d -> reply(id, "cancel", null));
            } else {
                String kind = String.valueOf(payload);
                dialog.setMessage(label(kind.equals("reload") ? "markdownReloadConfirm" : kind.equals("overwrite") ? "markdownOverwriteConfirm" : "markdownRelativeLinks"))
                        .setPositiveButton(label("confirm"), (d, w) -> reply(id, true, null)).setNegativeButton(label("cancel"), (d, w) -> reply(id, false, null))
                        .setOnCancelListener(d -> reply(id, false, null));
            }
            dialog.show(); return;
        }
        if (action.equals("new") || action.equals("recover")) { if (treePreparing) { reply(id, null, new IOException("unavailable")); return; } switchDocument(null, 0, action.equals("recover") ? String.valueOf(payload) : null); reply(id, JSONObject.NULL, null); return; }
        if (action.equals("link")) {
            Uri uri = Uri.parse(String.valueOf(payload));
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()) && !"mailto".equals(uri.getScheme())) { reply(id, null, new IOException("permission")); return; }
            new AlertDialog.Builder(this).setTitle(label("openBy")).setMessage(uri.toString()).setPositiveButton(label("confirm"), (d, w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); reply(id, JSONObject.NULL, null); } catch (Exception error) { reply(id, null, error); }
            }).setNegativeButton(label("cancel"), (d, w) -> reply(id, JSONObject.NULL, null)).setOnCancelListener(d -> reply(id, JSONObject.NULL, null)).show(); return;
        }
        long requestEpoch = switchEpoch;
        IO.execute(() -> {
            try {
                if (requestEpoch != switchEpoch || destroyed) throw new IOException("unavailable");
                Object result;
                if (action.startsWith("host:")) {
                    long generation = host.generation;
                    result = host.handle(action.substring(5), (JSONObject) payload);
                    if (host.generation != generation) { resources.clear(); resourceReferences.clear(); if (!treePreparing) updateTreeTarget(true); }
                }
                else switch (action) {
                    case "bootstrap": result = host.bootstrap(languages, language, dark()); break;
                    case "preferences": {
                        JSONObject preferences = host.preferences((JSONObject) payload);
                        preferredTheme = preferences.optString("theme", "system");
                        runOnUiThread(this::updateSystemBars);
                        result = preferences; break;
                    }
                    case "recoveries": {
                        org.json.JSONArray entries = host.recoveries();
                        for (int i = 0; i < entries.length(); i++) {
                            JSONObject entry = entries.getJSONObject(i);
                            if ("markdownCorruptRecovery".equals(entry.optString("displayName"))) entry.put("displayName", label("markdownCorruptRecovery"));
                        }
                        result = entries; break;
                    }
                    case "discard-recovery": host.discard(String.valueOf(payload)); result = JSONObject.NULL; break;
                    case "state": if (!(payload instanceof JSONObject) || !host.sessionId.equals(((JSONObject) payload).optString("sessionId"))) throw new IOException("protocol"); result = JSONObject.NULL; break;
                    case "cancel-save-as": host.cancelCandidate(String.valueOf(payload)); result = JSONObject.NULL; break;
                    case "resource": result = resolveResource(String.valueOf(payload)); break;
                    default: throw new IOException("protocol");
                }
                reply(id, result, null);
            } catch (Exception error) { reply(id, null, error); }
        });
    }

    private void updateTreeTarget(boolean resetOutside) {
        if (tree == null || destroyed) return;
        MarkdownHost selected = host; long epoch = switchEpoch;
        treeIO.execute(() -> {
            if (destroyed || epoch != switchEpoch || selected != host) return;
            try {
                if (selected.target == null) { tree.current(null); return; }
                MarkdownTree.Root root = tree.root();
                boolean inside = false;
                if (root != null) {
                    String key = treeFiles.localKey(root, selected.target);
                    if (key != null) try { inside = !treeFiles.path(root, key).isEmpty(); } catch (Exception ignored) { }
                    if (inside || !resetOutside) { tree.current(key); return; }
                }
                MarkdownTree.Root parent = treeFiles.parent(selected.target);
                if (destroyed || epoch != switchEpoch || selected != host) return;
                if (parent == null) tree.permissionRequired(); else tree.select(parent);
                tree.current(parent == null ? null : treeFiles.localKey(parent, selected.target));
            } catch (Exception ignored) { tree.permissionRequired(); }
            finally { if (!destroyed && epoch == switchEpoch) runOnUiThread(() -> command("tree-change", null)); }
        });
    }

    private void dispatchTree(String id, String action, Object payload) {
        long epoch = switchEpoch; MarkdownHost previous = host;
        if (action.equals("tree-save-view")) {
            try {
                MarkdownTree.CapturedView captured = tree.captureView((JSONObject) payload);
                ReplyToken token = replies.get(id); if (token != null) token.viewOnly = true;
                treeIO.execute(() -> {
                    try { tree.saveCapturedView(captured); reply(id, JSONObject.NULL, null); }
                    catch (Exception error) { reply(id, null, error); }
                });
            } catch (Exception error) { reply(id, null, error); }
            return;
        }
        if (action.equals("tree-choose-root")) {
            if (pickerAction != null || closing || treePreparing) { reply(id, MarkdownTree.blocked(new IOException("unavailable")), null); return; }
            pickerAction = "tree-root"; pickerRequest = id; pickerSwitchEpoch = epoch;
            treeFiles.cancelQueries();
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(Intent.EXTRA_TITLE, label("markdownOpenFolder"))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            // Picker navigation hint only; a leaf URI still grants no parent access.
            if (host.target != null) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, host.target.uri);
            try { picker.launch(intent); } catch (Exception error) { pickerAction = null; reply(id, MarkdownTree.blocked(error), null); }
            return;
        }
        if (action.equals("tree-commit-open") || action.equals("tree-cancel-open")) {
            IO.execute(() -> {
                try {
                    if (destroyed) throw new IOException("unavailable");
                    String ticket = String.valueOf(payload);
                    if (action.equals("tree-cancel-open")) { treeSwitch.cancel(ticket); treePreparing = treeSwitch.pending(); reply(id, JSONObject.NULL, null); return; }
                    MarkdownHost next = treeSwitch.commit(ticket, host);
                    if (next != host) {
                        host = next; switchEpoch++; resources.clear(); resourceReferences.clear(); commands.clear(); closing = false;
                        ReplyToken token = replies.get(id); if (token != null) token.documentEpoch = switchEpoch;
                        updateTreeTarget(false);
                    }
                    treePreparing = false;
                    reply(id, treeSwitch.committedBootstrap(ticket, host), null);
                } catch (Exception error) { reply(id, null, error); }
            });
            return;
        }
        if (action.equals("tree-prepare-open")) {
            if (treePreparing || pickerAction != null || closing) { reply(id, MarkdownTree.blocked(new IOException("unavailable")), null); return; }
            treePreparing = true;
        }
        treeIO.execute(() -> {
            try {
                if (destroyed || epoch != switchEpoch || previous != host) throw new IOException("unavailable");
                Object result;
                switch (action) {
                    case "tree-state": result = tree.state(); break;
                    case "tree-list": result = tree.list((JSONObject) payload); break;
                    case "tree-prepare-open": {
                        String key = tree.openKey((JSONObject) payload);
                        Uri selected = treeFiles.uri(tree.root(), key);
                        IO.execute(() -> {
                            try {
                                if (destroyed || epoch != switchEpoch || previous != host) throw new IOException("unavailable");
                                JSONObject ready = treeSwitch.prepare(host, selected, languages, language, dark());
                                treePreparing = "ready".equals(ready.getString("kind"));
                                reply(id, ready, null);
                            } catch (Exception error) { treePreparing = false; reply(id, MarkdownTree.blocked(error), null); }
                        });
                        return;
                    }
                    default: throw new IOException("protocol");
                }
                if (destroyed || epoch != switchEpoch || previous != host) throw new IOException("unavailable");
                reply(id, result, null);
            } catch (Exception error) {
                if (action.equals("tree-prepare-open")) treePreparing = false;
                if (action.equals("tree-save-view")) reply(id, null, error); else reply(id, MarkdownTree.blocked(error), null);
            }
        });
    }

    private Object resolveResource(String reference) {
        try {
            if (host.closed) return JSONObject.NULL;
            String previous = resourceReferences.get(host.sessionId + ":" + reference);
            if (previous != null) return previous;
            if (host.resourceTree == null) return JSONObject.NULL;
            if (resources.size() >= 256) return JSONObject.NULL;
            String mime = MarkdownFiles.imageMime(reference.split("[?#]", 2)[0]);
            if (mime == null) return JSONObject.NULL;
            Uri uri = host.files.resource(host.resourceTree, reference);
            String url = ORIGIN + "/resource/" + host.sessionId + "/" + MarkdownIds.token();
            resources.put(url, new Resource(uri, mime, host, false));
            resourceReferences.put(host.sessionId + ":" + reference, url);
            return url;
        } catch (Exception error) { return JSONObject.NULL; }
    }

    private String imageName(Uri source) throws Exception {
        String name = "";
        try (Cursor cursor = getContentResolver().query(source, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        }
        return name;
    }

    private void beginImageDestination(String request, Uri source, String name) {
        if (destroyed || isFinishing()) return;
        imageSource = source; imageName = name;
        if (host.resourceTree != null) { nameImage(request, host.resourceTree); return; }
        pickerAction = "image-directory"; pickerRequest = request;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try { picker.launch(intent); } catch (Exception error) { finishImage(request, object("kind", "blocked")); }
    }

    private void nameImage(String request, Uri directory) {
        if (destroyed || isFinishing()) return;
        imageDirectory = directory; pickerAction = "image-name";
        IO.execute(() -> {
            try {
                String folder = imageName(DocumentsContract.buildDocumentUriUsingTree(directory, DocumentsContract.getTreeDocumentId(directory)));
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    EditText name = new EditText(this);
                    name.setSingleLine(true); name.setText(imageName); name.selectAll();
                    new AlertDialog.Builder(this).setTitle(label("insertImage")).setMessage(folder).setView(name)
                            .setPositiveButton(label("save"), (dialog, which) -> {
                                String leaf = name.getText().toString();
                                if (!MarkdownImageImport.validName(leaf)) { finishImage(request, object("kind", "blocked")); return; }
                                pickerAction = "image-writing";
                                Uri source = imageSource, tree = imageDirectory;
                                IO.execute(() -> {
                                    try { finishImage(request, insertImage(source, tree, leaf)); }
                                    catch (Exception failure) { finishImage(request, object("kind", "blocked")); }
                                });
                            }).setNegativeButton(label("cancel"), (dialog, which) -> finishImage(request, object("kind", "cancelled")))
                            .setOnCancelListener(dialog -> finishImage(request, object("kind", "cancelled"))).show();
                });
            } catch (Exception error) { finishImage(request, object("kind", "blocked")); }
        });
    }

    private void finishImage(String request, JSONObject result) {
        runOnUiThread(() -> {
            imageSource = null; imageDirectory = null; imageName = null; pickerAction = null; pickerRequest = null;
            reply(request, result, null);
        });
    }

    private JSONObject insertImage(Uri source, Uri tree, String leaf) throws Exception {
        if (host.target == null || host.closed || tree == null || !host.files.isParent(tree, host.target) || resources.size() >= 256) return object("kind", "blocked");
        byte[] bytes;
        try (InputStream input = getContentResolver().openInputStream(source)) { bytes = MarkdownCodec.read(input, MarkdownImageImport.MAX_BYTES); }
        String parentId = DocumentsContract.getTreeDocumentId(tree);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId);
        Uri destination = MarkdownImageImport.copy(bytes, leaf, new MarkdownImageImport.Destination<Uri>() {
            @Override public Uri find(String name) throws Exception { return host.files.findChild(tree, parentId, name); }
            @Override public Uri create(String name, String mime) throws Exception { return DocumentsContract.createDocument(getContentResolver(), parent, mime, name); }
            @Override public String name(Uri file) throws Exception { return imageName(file); }
            @Override public byte[] read(Uri file) throws Exception {
                try (InputStream input = getContentResolver().openInputStream(file)) { return MarkdownCodec.read(input, MarkdownImageImport.MAX_BYTES); }
            }
            @Override public void write(Uri file, byte[] data) throws Exception { host.files.writeUri(file, data); }
            @Override public boolean same(Uri first, Uri second) { return first.getAuthority().equals(second.getAuthority()) && DocumentsContract.getDocumentId(first).equals(DocumentsContract.getDocumentId(second)); }
        });
        String reference = Uri.encode(leaf);
        String url = ORIGIN + "/resource/" + host.sessionId + "/" + MarkdownIds.token();
        resources.put(url, new Resource(destination, MarkdownFiles.imageMime(leaf), host, true));
        resourceReferences.put(host.sessionId + ":" + reference, url);
        return object("kind", "inserted").put("reference", reference);
    }

    private void reply(String id, Object result, Exception error) {
        if (id == null) return;
        ReplyToken token = replies.remove(id);
        if (token == null) return;
        JSONObject envelope = object("type", "reply");
        try { envelope.put("id", token.id); if (error == null && (token.viewOnly || token.documentEpoch == switchEpoch)) envelope.put("result", result == null ? JSONObject.NULL : result); else envelope.put("error", label("markdownError")); }
        catch (Exception ignored) { return; }
        runOnUiThread(() -> {
            if (token.epoch != pageEpoch || isDestroyed()) return;
            try {
                if (!token.viewOnly && token.documentEpoch != switchEpoch) { envelope.remove("result"); envelope.put("error", label("markdownError")); }
                token.proxy.postMessage(envelope.toString());
            } catch (Exception ignored) { /* Frame already gone. */ }
        });
    }

    private void command(String type, Runnable accepted) {
        if (replyProxy == null) {
            if (accepted != null) IO.execute(() -> {
                try {
                    if (host != null && !host.closed) {
                        try { MarkdownRegistry.INSTANCE.detach(host.sessionId); }
                        catch (Exception pendingWrite) {
                            // The renderer is gone and queued I/O has drained. Retain
                            // protected claims for recovery without trapping the user here.
                            MarkdownRegistry.INSTANCE.abandonCrashed(host.sessionId);
                        }
                        host.closed = true;
                    }
                    runOnUiThread(accepted);
                } catch (Exception error) { closing = false; showFailure(); }
            });
            return;
        }
        String id = MarkdownIds.newId();
        if (commands.containsKey(id)) { closing = false; showFailure(); return; }
        if (accepted != null) commands.put(id, accepted);
        try { replyProxy.postMessage(object("type", "command").put("command", object("id", id).put("type", type).put("dark", dark())).toString()); }
        catch (Exception error) { commands.remove(id); closing = false; }
    }

    private void switchDocument(Uri uri, int flags, String recovery) {
        if (closing || pickerAction != null || treePreparing) return;
        if (host != null && host.target != null && host.target.uri.equals(uri)) return;
        closing = true;
        IO.execute(() -> {
            try {
                if (uri != null) {
                    MarkdownFiles.Target selected = host.files.open(uri, flags);
                    if (host.target != null && selected.identity.equals(host.target.identity)) { runOnUiThread(() -> closing = false); return; }
                    if (MarkdownRegistry.INSTANCE.owner(selected.identity) != null) throw new IOException("occupied");
                }
                if (recovery != null) {
                    if (!recovery.startsWith("draft-") || host.store.read(recovery) == null) throw new IOException("unavailable");
                }
                runOnUiThread(() -> command("close", () -> IO.execute(() -> {
                    try {
                        MarkdownHost next = new MarkdownHost(this);
                        boolean failed = false;
                        try { if (recovery != null) next.restore(recovery); else if (uri != null) next.open(uri, flags); }
                        catch (Exception error) { failed = true; }
                        host = next; switchEpoch++; resources.clear(); resourceReferences.clear(); updateTreeTarget(true);
                        final boolean openFailed = failed;
                        runOnUiThread(() -> { closing = false; loadPage(); if (openFailed) showOperationFailure(); });
                    } catch (Exception error) { closing = false; showFailure(); }
                })));
            } catch (Exception error) { runOnUiThread(() -> { closing = false; showOperationFailure(); }); }
        });
    }

    private void showOperationFailure() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle(label("markdownEditor")).setMessage(label("markdownError"))
                .setPositiveButton(label("confirm"), null).show();
    }

    private void closeEditor() {
        if (closing) return;
        closing = true;
        command("close", () -> { if (host != null && !host.closed) IO.execute(() -> { try { MarkdownRegistry.INSTANCE.detach(host.sessionId); } catch (Exception ignored) { } }); finish(); });
    }

    private void showFailure() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this).setTitle(label("markdownEditor")).setMessage(label("markdownLoadError"))
                    .setPositiveButton(label("close"), (d, w) -> closeEditor()).show();
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (host != null) IO.execute(() -> { if (isMarkdownIntent(this, intent)) runOnUiThread(() -> switchDocument(intent.getData(), intent.getFlags(), null)); });
    }
    @Override protected void onStop() { if (replyProxy != null && !closing) command("background", null); super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (replyProxy != null) { command("external-change", null); command("tree-change", null); } }
    @Override public void onConfigurationChanged(Configuration configuration) { super.onConfigurationChanged(configuration); updateSystemBars(); if (replyProxy != null) command("theme", null); }
    @Override protected void onDestroy() {
        destroyed = true;
        if (treeFiles != null) treeFiles.cancel();
        treeIO.shutdown(); // Drain already accepted view snapshots; provider requests are cancelled above.
        resources.clear(); resourceReferences.clear(); replies.clear(); replyProxy = null;
        if (web != null) { WebViewCompat.removeWebMessageListener(web, "siyuanMarkdownNative"); web.destroy(); web = null; }
        IO.execute(() -> {
            // Read after all accepted startup/provider operations have drained.
            MarkdownHost previous = host;
            try {
                if (treeSwitch != null) treeSwitch.drain();
                if (previous != null && !previous.closed) MarkdownRegistry.INSTANCE.detach(previous.sessionId);
                if (activityLeaseAttached) MarkdownRegistry.INSTANCE.detach(activityLease);
            } catch (Exception ignored) {
                // I/O tasks ahead of this callback have drained. Keep their claims,
                // but expose durable drafts as crashed recoveries to a new container.
                if (previous != null) MarkdownRegistry.INSTANCE.abandonCrashed(previous.sessionId);
                if (activityLeaseAttached) MarkdownRegistry.INSTANCE.abandonCrashed(activityLease);
            }
        });
        // Its :markdown process owns no kernel and never invokes Mobile.exit.
        super.onDestroy();
    }
}
