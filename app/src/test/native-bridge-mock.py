#!/usr/bin/env python3
"""Compile/run the production boundary against small framework mocks.
Usage: python app/src/test/native-bridge-mock.py /path/to/org-json.jar [androidx-webkit-classes.jar]
The optional official AndroidX jar is used for a separate API-signature compile.
This does not emulate Chromium or replace device instrumentation.
"""
from pathlib import Path
import re, subprocess, sys, tempfile
src = Path(__file__).resolve().parents[1] / 'main/java/org/b3log/siyuan'
json_jar = str(Path(sys.argv[1]).resolve())
files = {
'android/net/Uri.java': '''package android.net; public class Uri { private String value; private Uri(String v){value=v;} public static Uri parse(String v){return new Uri(v);} public String toString(){return value;} }''',
'android/webkit/JavascriptInterface.java': '''package android.webkit; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface JavascriptInterface {}''',
'android/webkit/ValueCallback.java': '''package android.webkit; public interface ValueCallback<T> { void onReceiveValue(T value); }''',
'android/content/res/AssetManager.java': '''package android.content.res; public class AssetManager { public java.io.InputStream open(String name){return new java.io.ByteArrayInputStream("/* mock asset */".getBytes());} }''',
'android/content/Context.java': '''package android.content; public class Context { public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();} }''',
'android/webkit/WebView.java': '''package android.webkit; public class WebView { public java.util.Map<String,Object> bridges=new java.util.HashMap<>(); public void addJavascriptInterface(Object b,String n){bridges.put(n,b);} public void removeJavascriptInterface(String n){bridges.remove(n);} public android.content.Context getContext(){return new android.content.Context();} public void evaluateJavascript(String s,ValueCallback<String> v){} }''',
'org/b3log/siyuan/Utils.java': '''package org.b3log.siyuan; public class Utils { public static void logError(String a,String b,Exception e){} }''',
'androidx/webkit/JavaScriptReplyProxy.java': '''package androidx.webkit; public abstract class JavaScriptReplyProxy { public abstract void postMessage(String value); public abstract void postMessage(byte[] value); }''',
'androidx/webkit/ScriptHandler.java': '''package androidx.webkit; public interface ScriptHandler { void remove(); }''',
'androidx/webkit/WebMessageCompat.java': '''package androidx.webkit; public class WebMessageCompat { public static final int TYPE_STRING=0; private String data; public WebMessageCompat(String d){data=d;} public String getData(){return data;} public int getType(){return TYPE_STRING;} }''',
'androidx/webkit/WebViewFeature.java': '''package androidx.webkit; public class WebViewFeature { public static boolean supported=true; public static final String WEB_MESSAGE_LISTENER="listener", DOCUMENT_START_SCRIPT="start"; public static boolean isFeatureSupported(String n){return supported;} }''',
'androidx/webkit/WebViewCompat.java': '''package androidx.webkit; public class WebViewCompat { public static WebMessageListener listener; public static boolean failScript=false; public interface WebMessageListener { void onPostMessage(android.webkit.WebView view,WebMessageCompat message,android.net.Uri origin,boolean main,JavaScriptReplyProxy reply); } public static void addWebMessageListener(android.webkit.WebView v,String n,java.util.Set<String> origins,WebMessageListener l){if(!origins.equals(java.util.Collections.singleton("http://127.0.0.1:6806")))throw new AssertionError();listener=l;} public static ScriptHandler addDocumentStartJavaScript(android.webkit.WebView v,String source,java.util.Set<String> origins){if(failScript)throw new IllegalStateException();return ()->{};} public static void removeWebMessageListener(android.webkit.WebView v,String n){listener=null;} }'''
}
methods=re.findall(r'@JavascriptInterface\s+public (\w+) (\w+)\(([^)]*)\)',(src/'JSAndroid.java').read_text())
stub='package org.b3log.siyuan; public class JSAndroid { public int calls; public Runnable duringCall; '
for ret,name,params in methods:
    result={'void':'','String':'return "value";','int':'return 1080;','boolean':'return true;'}[ret]
    stub+=f'public {ret} {name}({params}){{calls++;if(duringCall!=null)duringCall.run();{result}}}\n'
files['org/b3log/siyuan/JSAndroid.java']=stub+'}'
files['org/b3log/siyuan/NativeBridgeBoundaryMockTest.java']='''package org.b3log.siyuan;
import android.webkit.WebView;
import android.net.Uri;
import androidx.webkit.*;
import org.json.*;
public class NativeBridgeBoundaryMockTest {
 static class Reply extends JavaScriptReplyProxy { String result; public void postMessage(String v){result=v;} public void postMessage(byte[] v){} }
 static Reply hello(WebView view,String origin,boolean main){Reply r=new Reply();WebViewCompat.listener.onPostMessage(view,new WebMessageCompat("{\\"id\\":1,\\"method\\":\\"hello\\",\\"args\\":[]}"),Uri.parse(origin),main,r);return r;}
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] ignored)throws Exception{
  WebView view=new WebView(); JSAndroid delegate=new JSAndroid(); NativeBridgeBoundary boundary=new NativeBridgeBoundary(view,delegate);
  check(boundary.install()); check(!view.bridges.containsKey("JSAndroid"));
  NativeBridgeBoundary.SyncBridge raw=(NativeBridgeBoundary.SyncBridge)view.bridges.get("SiYuanNativeSync");
  String origin=NativeBridgePolicy.ORIGIN;
  check(hello(view,origin,true).result==null);
  boundary.onNavigation(origin+"/stage/build/mobile/");
  check(hello(view,origin,false).result==null); check(hello(view,"https://evil.example",true).result==null);
  check(hello(view,"http://127.0.0.1:6807",true).result==null); check(hello(new WebView(),origin,true).result==null);
  check(raw.invoke("guess","readClipboard","[]").contains("error")); check(delegate.calls==0);
  String token=new JSONObject(hello(view,origin,true).result).getString("value");
  check(new JSONObject(raw.invoke(token,"readClipboard","[]")).getString("value").equals("value"));
  check(new JSONObject(raw.invoke(token,"prepareWordSelection","[\\"中文\\",-1,0]")).getBoolean("value"));
  check(delegate.calls==2);
  check(new JSONObject(raw.invoke(token,"getAVMapNativeBoundary","[]")).getJSONObject("value").getBoolean("enabled"));
  check(raw.invoke(token,"getClass","[]").contains("error"));
  check(raw.invoke(token,"readClipboard","[1]").contains("error"));
  check(raw.invoke(token,"setWebViewFocusable","[\\"true\\"]").contains("error"));
  check(raw.invoke(token,"cancelNotification","[1.5]").contains("error"));
  check(delegate.calls==2);
  boundary.onNavigation(origin+"/stage/build/mobile/");
  check(raw.invoke(token,"readClipboard","[]").contains("error"));
  String next=new JSONObject(hello(view,origin,true).result).getString("value"); check(!token.equals(next));
  check(raw.invoke(token,"getAVMapNativeBoundary","[]").contains("error"));
  check(!raw.invoke(next,"readClipboard","[]").contains("error"));
  delegate.duringCall=()->boundary.onNavigation(origin+"/stage/build/mobile/");
  check(raw.invoke(next,"readClipboard","[]").contains("error"));
  delegate.duringCall=null;
  boundary.onNavigation(origin+"/stage/map/wrapper.html?provider=amap"); check(hello(view,origin,true).result==null);
  boundary.onNavigation(origin+"/stage/map/index.html?provider=amap"); check(hello(view,origin,true).result==null);
  boundary.onNavigation(origin+"/assets/evil.html"); check(hello(view,origin,true).result==null);
  check(raw.invoke(next,"readClipboard","[]").contains("error"));
  boundary.close(); check(view.bridges.isEmpty()); check(WebViewCompat.listener==null);
  check(raw.invoke(next,"readClipboard","[]").contains("error"));
  WebViewFeature.supported=false; check(!new NativeBridgeBoundary(new WebView(),new JSAndroid()).install());
  WebViewFeature.supported=true; WebViewCompat.failScript=true;
  WebView partial=new WebView(); check(!new NativeBridgeBoundary(partial,new JSAndroid()).install());
  check(partial.bridges.isEmpty()); check(WebViewCompat.listener==null);
  System.out.println("Native bridge mock source/frame authorization, sync dispatch, token revocation, fallback and cleanup passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='siyuan-native-bridge-') as directory:
    work=Path(directory)
    for name,content in files.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
    production=[str(src/'NativeBridgePolicy.java'),str(src/'NativeBridgeBoundary.java')]
    mocks=[str(work/name) for name in files]
    if len(sys.argv)>2:
        real_mocks=[str(work/name) for name in files if not name.startswith('androidx/') and not name.endswith('MockTest.java')]
        subprocess.run(['java','com.sun.tools.javac.Main','-cp',json_jar+':'+str(Path(sys.argv[2]).resolve()),'-d',str(work/'api-classes'),*real_mocks,*production],check=True)
        print('Production bridge compiled against official AndroidX WebKit API (Android framework/delegate mocked)')
    subprocess.run(['java','com.sun.tools.javac.Main','-cp',json_jar,'-d',str(work/'classes'),*mocks,*production],check=True)
    subprocess.run(['java','-cp',str(work/'classes')+':'+json_jar,'org.b3log.siyuan.NativeBridgeBoundaryMockTest'],check=True)
