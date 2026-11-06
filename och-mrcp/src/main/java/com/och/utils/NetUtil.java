package com.och.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * 网络工具：获取本机对外可达的 IPv4 地址（排除回环/虚拟/WSL 网卡）。
 * 用于 SDP 应答中通告给 MRCP 客户端（FreeSWITCH）的连接地址。
 */
public final class NetUtil {

    private NetUtil() {}

    /**
     * 获取本机局域网 IPv4 地址。
     * 优先返回非回环、非虚拟、非 WSL/Hyper-V 网卡的 IPv4 地址。
     */
    public static String getLocalLanIp() {
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                String name = nic.getName().toLowerCase();
                String disp = nic.getDisplayName() != null ? nic.getDisplayName().toLowerCase() : "";
                // 跳过 WSL / Hyper-V / Docker / 虚拟网卡
                if (name.contains("veth") || name.contains("docker") || name.contains("br-")
                        || name.contains("wsl") || disp.contains("hyper-v")
                        || name.startsWith("vEthernet".toLowerCase())) {
                    continue;
                }
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        // 优先返回常见的局域网地址
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")
                                || ip.startsWith("172.")) {
                            return ip;
                        }
                        if (fallback == null) {
                            fallback = ip;
                        }
                    }
                }
            }
            if (fallback != null) {
                return fallback;
            }
        } catch (Exception e) {
            // ignore
        }
        return "127.0.0.1";
    }
}