const assert = require("node:assert/strict");
const {readFileSync, mkdtempSync, writeFileSync, rmSync} = require("node:fs");
const {join} = require("node:path");
const {tmpdir} = require("node:os");
const {execFileSync} = require("node:child_process");
const {test} = require("node:test");

test("LAN addresses exclude VPN and unusable interfaces without losing physical networks", () => {
    const source = readFileSync(join(__dirname, "../app/src/main/java/org/b3log/siyuan/Utils.java"), "utf8");
    const methods = source.slice(source.indexOf("public static String getLANIPAddressList"),
        source.indexOf("public static void logError"));
    const temporary = mkdtempSync(join(tmpdir(), "siyuan-lan-addresses-"));
    const harness = `import java.net.*;
import java.util.*;
public class LANAddressesHarness {
    ${methods}
    static void logError(String tag, String message, Exception error) { throw new AssertionError(error); }
    static class TextUtils {
        static String join(String separator, List<String> values) { return String.join(separator, values); }
    }
    static class Context {
        static final String CONNECTIVITY_SERVICE = "connectivity";
        final ConnectivityManager manager = new ConnectivityManager();
        Object getSystemService(String name) { return manager; }
    }
    static class Network {
        final NetworkCapabilities capabilities;
        final LinkProperties properties;
        Network(String address, int... transports) throws Exception {
            capabilities = new NetworkCapabilities(transports);
            properties = new LinkProperties(address);
        }
    }
    static class NetworkCapabilities {
        static final int TRANSPORT_WIFI = 1, TRANSPORT_ETHERNET = 2, TRANSPORT_CELLULAR = 3, TRANSPORT_VPN = 4;
        final int[] transports;
        NetworkCapabilities(int[] transports) { this.transports = transports; }
        boolean hasTransport(int transport) { return Arrays.stream(transports).anyMatch(value -> value == transport); }
    }
    static class LinkAddress {
        final InetAddress address;
        LinkAddress(String address) throws Exception { this.address = InetAddress.getByName(address); }
        InetAddress getAddress() { return address; }
    }
    static class LinkProperties {
        final List<LinkAddress> addresses;
        LinkProperties(String address) throws Exception { addresses = List.of(new LinkAddress(address)); }
        List<LinkAddress> getLinkAddresses() { return addresses; }
    }
    static class ConnectivityManager {
        final List<Network> networks = new ArrayList<>();
        Network active;
        Network getActiveNetwork() { return active; }
        Network[] getAllNetworks() { return networks.toArray(new Network[0]); }
        NetworkCapabilities getNetworkCapabilities(Network network) { return network.capabilities; }
        LinkProperties getLinkProperties(Network network) { return network.properties; }
    }
    static class NetworkInterface {
        static final List<NetworkInterface> interfaces = new ArrayList<>();
        final InetAddress address;
        boolean up = true, loopback, pointToPoint, multicast = true;
        NetworkInterface(String address) throws Exception { this.address = InetAddress.getByName(address); interfaces.add(this); }
        static Enumeration<NetworkInterface> getNetworkInterfaces() { return Collections.enumeration(interfaces); }
        boolean isUp() { return up; }
        boolean isLoopback() { return loopback; }
        boolean isPointToPoint() { return pointToPoint; }
        boolean supportsMulticast() { return multicast; }
        Enumeration<InetAddress> getInetAddresses() { return Collections.enumeration(List.of(address)); }
    }
    static void check(String expected, Context context) {
        String actual = getLANIPAddressList(context);
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        Network wifi = new Network("192.168.1.110", NetworkCapabilities.TRANSPORT_WIFI);
        Network vpn = new Network("172.19.0.1", NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_VPN);
        Network ethernet = new Network("192.168.2.2", NetworkCapabilities.TRANSPORT_ETHERNET);
        Network cellular = new Network("10.0.0.2", NetworkCapabilities.TRANSPORT_CELLULAR);
        context.manager.networks.addAll(List.of(wifi, vpn, ethernet, cellular));
        context.manager.active = vpn;
        new NetworkInterface("192.168.1.110");
        new NetworkInterface("172.19.0.1");
        new NetworkInterface("10.0.0.2");
        new NetworkInterface("192.168.43.1");
        new NetworkInterface("192.168.122.1").up = false;
        new NetworkInterface("172.18.0.1").multicast = false;
        new NetworkInterface("172.20.0.1").pointToPoint = true;
        new NetworkInterface("127.0.0.1").loopback = true;
        new NetworkInterface("203.0.113.1");
        new NetworkInterface("fd00::1");
        check("192.168.1.110,192.168.2.2,192.168.43.1,127.0.0.1", context);
        context.manager.active = wifi;
        check("192.168.1.110,192.168.2.2,192.168.43.1,127.0.0.1", context);
        context.manager.active = null;
        check("192.168.1.110,192.168.2.2,192.168.43.1,127.0.0.1", context);
        NetworkInterface.interfaces.clear();
        context.manager.networks.clear();
        check("127.0.0.1", context);
    }
}`;
    try {
        writeFileSync(join(temporary, "LANAddressesHarness.java"), harness, "utf8");
        execFileSync("javac", ["-encoding", "UTF-8", "-d", temporary, join(temporary, "LANAddressesHarness.java")]);
        execFileSync("java", ["-cp", temporary, "LANAddressesHarness"]);
    } finally {
        assert.equal(require("node:path").dirname(temporary), tmpdir());
        rmSync(temporary, {recursive: true, force: true});
    }
});
