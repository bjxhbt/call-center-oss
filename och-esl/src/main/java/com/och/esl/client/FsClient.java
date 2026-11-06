package com.och.esl.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.och.common.constant.EslConstant;
import com.och.common.domain.CallInfo;
import com.och.common.domain.ChannelInfo;
import com.och.common.enums.DirectionEnum;
import com.och.common.enums.EslEventFormat;
import com.och.common.enums.GatewayTypeEnum;
import com.och.common.thread.ThreadFactoryImpl;
import com.och.esl.FsEslMsg;
import com.och.esl.propeties.FsClientProperties;
import com.och.esl.service.IFsCallCacheService;
import com.och.esl.service.IFsEslEventService;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.entity.FsConfig;
import com.och.system.domain.entity.FsSipGateway;
import com.och.system.domain.query.fsconfig.FsConfigQuery;
import com.och.system.service.ICallRecordService;
import com.och.system.service.IFsConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.freeswitch.esl.client.IEslEventListener;
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.SendMsg;
import org.freeswitch.esl.client.transport.event.EslEvent;
import org.freeswitch.esl.client.transport.message.EslMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * @author danmo
 * @date 2023濡?6闂?9闂?9:49
 */
@Slf4j
public class FsClient {
    private static final String LOOPBACK_HOST = "127.0.0.1";
    private static final Pattern WEB_RTC_TRANSPORT = Pattern.compile(
            "(?i)(?:^|[;?&])transport=wss?(?=$|[;?&>])");
    /**
     * A connected inbound ESL client continuously receives heartbeat/custom events in
     * the deployed FreeSWITCH profile.  Keep this intentionally conservative so a
     * stale event never masks a real disconnect for long.
     */
    private static final long EVENT_CHANNEL_FRESHNESS_MILLIS = TimeUnit.MINUTES.toMillis(2);


    //闂佸憡绋撻崢褑銇愰妸褏纾介柡宥庡亞閸?
    @Value("${freeswitch.codec:^^:G722:PCMU:PCMA}")
    private String codec;

    @Value("${freeswitch.sample.rate:8000}")
    protected String sampleRate;

    @Value("${freeswitch.group:}")
    private String groupName;

    @Value("${och.ivr.agent-ring-timeout-seconds:120}")
    private int internalRingTimeoutSeconds;

    /**
     * 内部坐席呼叫编解码串；空=沿用旧自动协商行为。
     * 默认 PCMA 优先对齐外部网关腿(线上主叫 PCMA、坐席协商成 PCMU 导致媒体协商失败、话机不振铃)。
     */
    @Value("${freeswitch.internal-codec:^^:PCMA:PCMU}")
    private String internalCodec;

    /** 僵尸振铃清理阈值(秒)：callstate=RINGING 存活超过该值即 uuid_kill；0=关闭。 */
    @Value("${freeswitch.stuck-ring-timeout-seconds:600}")
    private int stuckRingTimeoutSeconds;

    @Value("${freeswitch.stale-channel-timeout-seconds:21600}")
    private long staleChannelTimeoutSeconds;


    private Map<String, Client> fsClientMap = new ConcurrentHashMap<>();

    /** Last successfully delivered event per configured ESL address. */
    private final Map<String, Long> lastEventReceivedAt = new ConcurrentHashMap<>();

    // Thread pool for synchronous internal originate calls (api mode, not bgapi)
    // bgapi + origination_uuid breaks FreeSWITCH multi-contact forking
    private final ScheduledExecutorService originateThreadPool = new ScheduledThreadPoolExecutor(4, new ThreadFactoryImpl("originate-pool-"));

    private ScheduledExecutorService checkFsThread = new ScheduledThreadPoolExecutor(1, new ThreadFactoryImpl("check-fs-pool-"));
    private final ScheduledExecutorService connectFsThread = new ScheduledThreadPoolExecutor(2, new ThreadFactoryImpl("connect-fs-pool-"));


    private final FsClientProperties clientProperties;
    private final IFsConfigService iFsConfigService;


    @Autowired
    private IFsEslEventService ILfsEslEventService;

    @Autowired
    protected IFsCallCacheService fsCallCacheService;

    @Autowired(required = false)
    private ICallRecordService callRecordService;

    public FsClient(IFsConfigService iFsConfigService, FsClientProperties clientProperties) {
        this.iFsConfigService = iFsConfigService;
        this.clientProperties = clientProperties;
    }


    public void init() {
        connectFsThread.execute(this::connectConfiguredServers);
        checkFsThread.scheduleAtFixedRate(() -> {
            try {
                checkConnect();
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }, 2, 1, TimeUnit.MINUTES);

    }

    private void connectConfiguredServers() {
        FsConfigQuery query = new FsConfigQuery();
        query.setGroup(groupName);
        List<FsConfig> fsConfigs = iFsConfigService.getList(query);
        for (FsConfig fsConfig : fsConfigs) {
            connect(fsConfig);
        }
    }

    private void connect(FsConfig server) {
        String ipAndPort = server.getIp() + EslConstant.CO + server.getPort();
        Client client = null;
        try {
            try {
                client = connect(server, resolveConnectHost(server.getIp()), ipAndPort);
            } catch (InboundConnectionFailure e) {
                if (!shouldTryLoopback(server.getIp())) {
                    throw e;
                }
                log.warn("Connect failed for {}, retry with loopback host {}", ipAndPort, LOOPBACK_HOST);
                client = connect(server, LOOPBACK_HOST, ipAndPort);
            }

            fsClientMap.put(ipAndPort, client);
            if (server.getStatus() == 1) {
                server.setStatus(0);
                updateFsConfigStatus(server);
            }
        } catch (InboundConnectionFailure e) {
            log.error("Connect failed msg:{}", e.getMessage(), e);
            closeClient(client);
            if (server.getStatus() == 0) {
                server.setStatus(1);
                updateFsConfigStatus(server);
            }
        }
    }

    private Client connect(FsConfig server, String host, String ipAndPort) throws InboundConnectionFailure {
        Client client = new Client();
        client.addEventListener(new IEslEventListener() {

            @Override
            public void eventReceived(EslEvent event) {
                sendRocketMqMsg(event);
            }

            @Override
            public void backgroundJobResultReceived(EslEvent event) {
                sendRocketMqMsg(event);
            }

            private void sendRocketMqMsg(EslEvent event) {
                lastEventReceivedAt.put(ipAndPort, System.currentTimeMillis());
                FsEslMsg lfsEslMsg = FsEslMsg.builder().eslEvent(event).address(ipAndPort).build();
                ILfsEslEventService.eslEventPublisher(lfsEslMsg);
            }
        });

        client.connect(host, server.getPort(), server.getPassword(), getConnectTimeout(server));
        client.setEventSubscriptions(EslEventFormat.PLAIN.getText(), "all");
        if (!client.canSend()) {
            closeClient(client);
            throw new InboundConnectionFailure("ESL client is not writable after connect to " + host + ":" + server.getPort());
        }
        return client;
    }

    private String resolveConnectHost(String host) {
        if (!isLocalAddress(host)) {
            return host;
        }
        if (!StringUtils.equals(host, LOOPBACK_HOST)) {
            log.info("ESL host {} is local address, connect with {}", host, LOOPBACK_HOST);
        }
        return LOOPBACK_HOST;
    }

    private boolean isLocalAddress(String host) {
        if (StringUtils.isBlank(host)) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || NetworkInterface.getByInetAddress(address) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean shouldTryLoopback(String host) {
        return StringUtils.isNotBlank(host)
                && !StringUtils.equals(host, LOOPBACK_HOST)
                && !StringUtils.equalsIgnoreCase(host, "localhost");
    }

    private void closeClient(Client client) {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.warn("Close fs client failed", e);
            }
        }
    }
    private int getConnectTimeout(FsConfig server) {
        Integer outTime = server.getOutTime();
        if (outTime == null || outTime <= 0) {
            return 5000;
        }
        return Math.min(outTime, 10000);
    }

    /**
     * fs闁哄鏅濋崑鐐垫暜閹绢喗鍋愰柤鍝ヮ暯閸嬫挻鎷呯拠鈩冣挄闂?     */
    private void checkConnect() {
        FsConfigQuery query = new FsConfigQuery();
        query.setGroup(groupName);
        List<FsConfig> fsConfigs = iFsConfigService.getList(query);
        for (FsConfig server : fsConfigs) {
            try {
                String clientUrl = server.getIp() + ":" + server.getPort();
                if (fsClientMap.containsKey(clientUrl)) {
                    Client client = fsClientMap.get(clientUrl);
                    if (client == null || !client.canSend()) {
                        fsClientMap.remove(clientUrl);
                        closeClient(client);
                        server.setStatus(1);
                        updateFsConfigStatus(server);
                        connect(server);
                    }
                } else {
                    connect(server);
                }
            } catch (Exception e) {
                log.error("checkConnect failed for " + server.getIp() + ":" + server.getPort(), e);
            }
        }
        for (FsConfig server : fsConfigs) {
            String address = server.getIp() + ":" + server.getPort();
            try {
                reapStaleChannels(address);
            } catch (Exception e) {
                log.warn("reapStaleChannels failed (ignored) address:{} err:{}", address, e.getMessage());
            }
        }
    }

    /**
     * 清扫已结束话单、超时振铃腿及超过通用阈值的陈旧通道。
     */
    private void reapStaleChannels(String address) {
        if (fsClientMap == null || !fsClientMap.containsKey(address) || !fsClientMap.get(address).canSend()) {
            return;
        }
        String body = sendApiText(address, "show", "channels as json");
        if (StringUtils.isBlank(body)) {
            return;
        }
        try {
            JSONObject json = JSONObject.parseObject(body);
            JSONArray rows = json.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) {
                return;
            }
            long nowEpoch = System.currentTimeMillis() / 1000L;
            Map<String, Boolean> endedCallCache = new ConcurrentHashMap<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String uuid = row.getString("uuid");
                if (StringUtils.isBlank(uuid)) {
                    continue;
                }
                String callId = getChannelCallId(address, uuid);
                if (StringUtils.isNotBlank(callId)
                        && endedCallCache.computeIfAbsent(callId, this::isEndedCall)) {
                    log.warn("Reap ended call channel: uuid={}, name={}, callId={}",
                            uuid, row.getString("name"), callId);
                    killCall(address, null, uuid);
                    continue;
                }
                long age = channelAgeSeconds(row, nowEpoch);
                if (staleChannelTimeoutSeconds > 0 && age > staleChannelTimeoutSeconds) {
                    log.warn("Reap stale channel (age {}s > {}s): uuid={}, name={}, callId={}",
                            age, staleChannelTimeoutSeconds, uuid, row.getString("name"), callId);
                    killCall(address, null, uuid);
                    continue;
                }
                if (stuckRingTimeoutSeconds <= 0) {
                    continue;
                }
                if (!"outbound".equalsIgnoreCase(row.getString("direction"))) {
                    continue;
                }
                String callState = row.getString("callstate");
                String state = row.getString("state");
                boolean ringingLike = "RINGING".equalsIgnoreCase(callState)
                        || "CS_CONSUME_MEDIA".equalsIgnoreCase(state);
                if (!ringingLike) {
                    continue;
                }
                if (age > stuckRingTimeoutSeconds) {
                    log.warn("Reap stuck ringing channel (age {}s > {}s): uuid={}, name={}, callId={}",
                            age, stuckRingTimeoutSeconds, uuid, row.getString("name"), callId);
                    killCall(address, null, uuid);
                }
            }
        } catch (Exception e) {
            log.warn("reapStuckRingingChannels parse failed (ignored): {}", e.getMessage());
        }
    }

    static long channelAgeSeconds(JSONObject row, long nowEpoch) {
        try {
            long createdEpoch = row.getLongValue("created_epoch");
            return createdEpoch > 0 ? nowEpoch - createdEpoch : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private boolean isEndedCall(String callId) {
        if (callRecordService == null || StringUtils.isBlank(callId)) {
            return false;
        }
        try {
            CallRecord callRecord = callRecordService.getByCallId(callId);
            return callRecord != null && callRecord.getCallEndTime() != null;
        } catch (Exception e) {
            log.warn("Check ended call failed (ignored), callId:{} err:{}", callId, e.getMessage());
            return false;
        }
    }

    private void updateFsConfigStatus(FsConfig server) {
        iFsConfigService.updateStatusById(server.getId(), server.getStatus());
    }


    public void destroy() {
        log.info("destroy fsClient");
        fsClientMap.forEach((path, client) -> {
            if (client.canSend()) {
                client.close();
            }
        });
        checkFsThread.shutdown();
        connectFsThread.shutdown();
        originateThreadPool.shutdown();
        fsClientMap = null;
        ILfsEslEventService.destroyThreadPool();
    }


    /**
     * 闂佸憡鐟﹂崹鍧楀焵椤戣法鍔嶇紒澶屽厴楠?     */
    public boolean sendMsg(String address, SendMsg msg) {
        if (fsClientMap.containsKey(address) && fsClientMap.get(address).canSend()) {
            fsClientMap.get(address).sendMessage(msg);
            return true;
        }
        return false;
    }

    /**
     * 閻庢鍠栭崐褰掝敆閻愬搫鐭楅柟杈捐吂閸嬫挻鎷呴崷顓夈儵鏌?     */
    public String sendAsyncMsg(String address, String cmd, String args) {
        if (fsClientMap.containsKey(address) && fsClientMap.get(address).canSend()) {
            log.info("fs 闂佸憡鐟﹂崹鍧楀焵椤戣法顦︾紒杈ㄦ尭椤垽濡烽敂鎷樸儵鏌?{}+{}", cmd, args);
            return fsClientMap.get(address).sendAsyncApiCommand(cmd, args);
        }
        return null;
    }

    /**
     * 闂佸憡鐟﹂崹鍧楀焵椤戣法鍔嶇紒澶屽厴楠?     */
    public EslMessage sendSyncMsg(String address, String cmd, String args) {
        if (fsClientMap.containsKey(address) && fsClientMap.get(address).canSend()) {
            return fsClientMap.get(address).sendSyncApiCommand(cmd, args);
        }
        return null;
    }

    /**
     * 执行 API 命令并返回纯文本 body(用于 sofia status 等查询类命令)。
     */
    public String sendApiText(String address, String cmd, String args) {
        EslMessage message = sendSyncMsg(address, cmd, args);
        return getMessageBody(message);
    }

    public boolean isUserRegistered(String address, String user) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(user)) {
            return false;
        }
        try {
            String contact = resolveUserContact(address, user);
            boolean registered = StringUtils.isNotBlank(contact);
            log.info("FreeSWITCH registration check user:{}, registered:{}, contact:{}",
                    user, registered, contact);
            return registered;
        } catch (Exception exception) {
            log.error("FreeSWITCH registration check failed, user:{}", user, exception);
            return false;
        }
    }

    /**
     * Checks whether the extension has an active browser/WebRTC contact.
     * A normal UDP/TCP SIP phone must not satisfy this check.
     */
    public boolean isWebRtcUserRegistered(String address, String user) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(user)) {
            return false;
        }
        try {
            String contact = resolveWebRtcUserContact(address, user);
            boolean registered = StringUtils.isNotBlank(contact);
            log.info("FreeSWITCH browser registration check user:{}, registered:{}, contact:{}",
                    user, registered, contact);
            return registered;
        } catch (Exception exception) {
            log.error("FreeSWITCH browser registration check failed, user:{}", user, exception);
            return false;
        }
    }

    public boolean hasActiveCallForAgent(String address, String agentNumber) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(agentNumber)) {
            return false;
        }
        try {
            String body = sendApiText(address, "show", "channels as json");
            if (StringUtils.isBlank(body)) {
                return false;
            }
            JSONObject json = JSONObject.parseObject(body);
            JSONArray rows = json.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) {
                return false;
            }
            for (int index = 0; index < rows.size(); index++) {
                if (isAgentChannel(rows.getJSONObject(index), agentNumber)) {
                    return true;
                }
            }
        } catch (Exception exception) {
            log.warn("Check active agent call failed, agentNumber:{}, error:{}",
                    agentNumber, exception.getMessage());
        }
        return false;
    }

    private String resolveUserContact(String address, String user) {
        String contact = StringUtils.trimToEmpty(sendApiText(address, "sofia_contact", "user/" + user));
        if (!isUsableContact(contact)) {
            contact = StringUtils.trimToEmpty(sendApiText(address, "sofia_contact", user));
        }
        return isUsableContact(contact) ? contact : "";
    }

    private String resolveWebRtcUserContact(String address, String user) {
        String directoryContacts = StringUtils.trimToEmpty(
                sendApiText(address, "sofia_contact", "user/" + user));
        String directContacts = StringUtils.trimToEmpty(
                sendApiText(address, "sofia_contact", user));
        return filterWebRtcContacts(directoryContacts + "," + directContacts);
    }

    String filterWebRtcContacts(String contacts) {
        List<String> endpoints = new ArrayList<>();
        if (StringUtils.isNotBlank(contacts)) {
            for (String candidate : contacts.split(",")) {
                String endpoint = StringUtils.trim(candidate);
                boolean webRtcContact = StringUtils.startsWithIgnoreCase(endpoint, "verto/")
                        || hasWebRtcTransport(endpoint);
                if (webRtcContact && isUsableContact(endpoint) && !endpoints.contains(endpoint)) {
                    endpoints.add(endpoint);
                }
            }
        }
        return String.join(",", endpoints);
    }

    private boolean hasWebRtcTransport(String endpoint) {
        int fsPathIndex = StringUtils.indexOfIgnoreCase(endpoint, ";fs_path=");
        String contactUri = fsPathIndex < 0 ? endpoint : endpoint.substring(0, fsPathIndex);
        return WEB_RTC_TRANSPORT.matcher(contactUri).find();
    }

    private boolean isUsableContact(String contact) {
        return StringUtils.isNotBlank(contact)
                && !StringUtils.startsWithIgnoreCase(contact, "error/")
                && !StringUtils.startsWithIgnoreCase(contact, "-ERR")
                && !"_undef_".equalsIgnoreCase(contact);
    }

    /**
     * 返回任意一个已连接的 FreeSWITCH ESL 地址(ip:port)，无可用连接返回 null。
     */
    public String anyAddress() {
        if (fsClientMap == null || fsClientMap.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, Client> entry : fsClientMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().canSend()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns a safe, read-only snapshot of command and event-channel health.
     *
     * <p>Do not derive event health only from {@link Client#canSend()}.  Inbound ESL
     * event delivery is asynchronous, and this status is also consumed on HTTP worker
     * threads.  A recently delivered event is stronger evidence than reporting the
     * entire ESL connection as disconnected merely because command writability cannot
     * be sampled at that instant.</p>
     */
    public FsConnectionStatus connectionStatus() {
        String commandAddress = anyAddress();
        String eventAddress = null;
        Long lastEventAt = null;
        if (lastEventReceivedAt != null && !lastEventReceivedAt.isEmpty()) {
            for (Map.Entry<String, Long> entry : lastEventReceivedAt.entrySet()) {
                Long timestamp = entry.getValue();
                if (timestamp != null && (lastEventAt == null || timestamp > lastEventAt)) {
                    eventAddress = entry.getKey();
                    lastEventAt = timestamp;
                }
            }
        }
        Long eventAgeMillis = lastEventAt == null ? null : Math.max(0L, System.currentTimeMillis() - lastEventAt);
        boolean eventChannelActive = commandAddress != null
                || (eventAgeMillis != null && eventAgeMillis <= EVENT_CHANNEL_FRESHNESS_MILLIS);
        return new FsConnectionStatus(commandAddress, eventAddress, lastEventAt, eventAgeMillis, eventChannelActive);
    }

    /**
     * 查询 sofia 网关状态: 返回 "sofia status gateway &lt;name&gt;" 或 "sofia status" 的原始文本。
     *
     * @param address      FreeSWITCH ESL 地址(ip:port)
     * @param gatewayName  网关名称，为空则查询全部(sofia status)
     */
    public String sofiaStatusGateway(String address, String gatewayName) {
        if (StringUtils.isBlank(gatewayName)) {
            return sendApiText(address, "sofia", "status");
        }
        return sendApiText(address, "sofia", "status gateway " + gatewayName.trim());
    }

    /**
     * 重新扫描 profile(加载新增/修改的动态网关，避免重复网关): sofia profile external rescan
     */
    public String sofiaProfileRescan(String address, String profile) {
        String p = StringUtils.isBlank(profile) ? "external" : profile.trim();
        log.info("sofia profile {} rescan, address:{}", p, address);
        return sendApiText(address, "sofia", "profile " + p + " rescan");
    }

    /**
     * 杀掉指定 profile 下的某个网关: sofia profile external killgw &lt;name&gt;
     */
    public String sofiaProfileKillgw(String address, String profile, String gatewayName) {
        String p = StringUtils.isBlank(profile) ? "external" : profile.trim();
        if (StringUtils.isBlank(gatewayName)) {
            log.warn("killgw ignored: gatewayName is blank, profile:{}", p);
            return "";
        }
        log.info("sofia profile {} killgw {}, address:{}", p, gatewayName, address);
        return sendApiText(address, "sofia", "profile " + p + " killgw " + gatewayName.trim());
    }

    /**
     * 重启 profile(重新加载全部网关): sofia profile external restart
     * 注意: restart 会短暂中断注册，仅在必要时使用。
     */
    public String sofiaProfileRestart(String address, String profile) {
        String p = StringUtils.isBlank(profile) ? "external" : profile.trim();
        log.info("sofia profile {} restart, address:{}", p, address);
        return sendApiText(address, "sofia", "profile " + p + " restart");
    }

    /**
     * uuid闁圭厧鐡ㄥ濠氭偤?
     *
     * @param address
     * @param uniqueId
     */
    public void answer(String address, String uniqueId) {
        SendMsg answer = new SendMsg(uniqueId);
        answer.addCallCommand(EslConstant.EXECUTE);
        answer.addExecuteAppName(EslConstant.ANSWER);
        sendMsg(address, answer);
    }

    public void ringReady(String address, String uniqueId) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId)) {
            return;
        }
        sendSyncMsg(address, "uuid_ring_ready", uniqueId);
    }

    /**
     * 闂佸湱顭堥崐鐟扳攦閳?
     *
     * @param address
     * @param callId
     * @param uniqueId
     */
    public void hangupCall(String address, Long callId, String uniqueId) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId)) {
            log.info("address:{} or uniqueId:{} is null", address, uniqueId);
            return;
        }

        SendMsg hangupMsg = new SendMsg(uniqueId);
        hangupMsg.addCallCommand(EslConstant.EXECUTE);
        hangupMsg.addExecuteAppName(EslConstant.HANGUP);
        hangupMsg.addExecuteAppArg(EslConstant.NORMAL_CLEARING);
        log.info("hangup call:{}, uniqueId:{}", callId, uniqueId);
        this.sendMsg(address, hangupMsg);
    }

    /**
     * 閻庢鍠栭幖顐﹀春濡ゅ懎绠伴柛灞剧⊕缁?
     *
     * @param address
     * @param callId
     * @param uniqueId
     */
    public void killCall(String address, Long callId, String uniqueId) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId)) {
            log.info("address:{} or uniqueId:{} is null", address, uniqueId);
            return;
        }
        log.info("kill call:{}, uniqueId:{}", callId, uniqueId);
        this.sendSyncMsg(address, "uuid_kill", uniqueId + EslConstant.SPACE + EslConstant.NORMAL_CLEARING);
    }

    public void killCall(Long callId, String uniqueId) {
        if (StringUtils.isBlank(uniqueId)) {
            log.info("uniqueId:{} is null", uniqueId);
            return;
        }
        if (fsClientMap == null || fsClientMap.isEmpty()) {
            log.info("fs client is empty, kill call:{}, uniqueId:{}", callId, uniqueId);
            return;
        }
        fsClientMap.keySet().forEach(address -> killCall(address, callId, uniqueId));
    }

    public void killCallsByCallId(Long callId) {
        killCallsByCallId(callId, false);
    }

    /**
     * 按 callId 清理通道。
     *
     * @param callerHungUp true=主叫已挂断场景的强清理:跳过"满意度保护"直接 hupall。
     *                     坐席 originate 腿(&park)独立于主叫,主叫挂断后 FreeSWITCH 不会自动取消它们;
     *                     满意度保护仅面向"坐席挂断、主叫存活转满意度"场景,主叫已挂时无可保护,
     *                     不强杀会导致坐席话机持续振铃直到 originate_timeout(线上实测滞留 2 小时)。
     */
    public void killCallsByCallId(Long callId, boolean callerHungUp) {
        if (Objects.isNull(callId)) {
            log.info("callId is null, skip kill call channels");
            return;
        }
        if (fsClientMap == null || fsClientMap.isEmpty()) {
            log.info("fs client is empty, kill call channels:{}", callId);
            return;
        }
        fsClientMap.keySet().forEach(address -> killCallsByCallId(address, callId, callerHungUp));
    }

    private void killCallsByCallId(String address, Long callId) {
        killCallsByCallId(address, callId, false);
    }

    private void killCallsByCallId(String address, Long callId, boolean callerHungUp) {
        String expectedCallId = String.valueOf(callId);
        // 检查是否有需要满意度评价的通话，若有则跳过hupall(避免挂断主叫)
        CallInfo cachedInfo = fsCallCacheService.getCallInfo(callId);
        boolean skipHupall = cachedInfo != null && cachedInfo.getFlowDataContext() != null && !callerHungUp;
        if (!skipHupall) {
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "och_call_id" + EslConstant.SPACE + expectedCallId);
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "sip_h_X-OCH-Call-ID" + EslConstant.SPACE + expectedCallId);
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "variable_och_call_id" + EslConstant.SPACE + expectedCallId);
            log.info("hupall call channels by callId:{}", callId);
        } else {
            log.info("检测到满意度流程上下文，跳过hupall callId:{}", callId);
        }

        EslMessage channelsMsg = sendSyncMsg(address, "show", "channels as json");
        String body = getMessageBody(channelsMsg);
        if (StringUtils.isBlank(body)) {
            log.info("show channels is empty, callId:{}", callId);
            return;
        }
        try {
            JSONObject json = JSONObject.parseObject(body);
            JSONArray rows = json.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) {
                log.info("show channels has no rows, callId:{}", callId);
                return;
            }

            Set<String> killUuids = new HashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String uuid = row.getString("uuid");
                if (StringUtils.isBlank(uuid)) {
                    continue;
                }
                if (isCallIdChannel(address, uuid, expectedCallId)) {
                    killUuids.add(uuid);
                }
            }

            if (killUuids.isEmpty()) {
                log.info("no matched call channels, callId:{}", callId);
                return;
            }

            killUuids.forEach(uuid -> {
                // 跳过主叫通道若有流程上下文
                if (!callerHungUp && cachedInfo != null && cachedInfo.getFlowDataContext() != null
                        && cachedInfo.getChannelMap() != null && cachedInfo.getChannelMap().get(uuid) != null
                        && Objects.equals(cachedInfo.getChannelMap().get(uuid).getDirectionType(), 1)) {
                    log.info("跳过主叫通道(保留满意度评价) callId:{}, uuid:{}", callId, uuid);
                } else {
                    log.info("kill call channel by callId, callId:{}, uuid:{}", callId, uuid);
                    killCall(address, callId, uuid);
                }
            });
        } catch (Exception e) {
            log.error("kill call channels failed, callId:{}, body:{}", callId, body, e);
        }
    }

    /**
     * 閻熸粎澧楀ú鐔兼偂?
     *
     * @param address
     * @param callId
     * @param uniqueId
     * @param filePath
     */
    public void killCallsByAgentNumber(String agentNumber) {
        if (StringUtils.isBlank(agentNumber)) {
            log.info("agentNumber is null, skip kill agent calls");
            return;
        }
        if (fsClientMap == null || fsClientMap.isEmpty()) {
            log.info("fs client is empty, kill agent calls:{}", agentNumber);
            return;
        }
        fsClientMap.keySet().forEach(address -> killCallsByAgentNumber(address, agentNumber));
    }

    private void killCallsByAgentNumber(String address, String agentNumber) {
        // 检查是否有需要满意度评价的通话，若有则跳过hupall(避免挂断主叫)
        boolean skipHupall = checkSatisfactionFlow(address, agentNumber);
        if (!skipHupall) {
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "sip_from_user" + EslConstant.SPACE + agentNumber);
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "sip_to_user" + EslConstant.SPACE + agentNumber);
            sendSyncMsg(address, "hupall", EslConstant.NORMAL_CLEARING + EslConstant.SPACE + "user_name" + EslConstant.SPACE + agentNumber);
            log.info("hupall call channels by number:{}", agentNumber);
        } else {
            log.info("检测到满意度流程上下文，跳过hupall agentNumber:{}", agentNumber);
        }

        EslMessage channelsMsg = sendSyncMsg(address, "show", "channels as json");
        String body = getMessageBody(channelsMsg);
        if (StringUtils.isBlank(body)) {
            log.info("show channels is empty, agentNumber:{}", agentNumber);
            return;
        }
        try {
            JSONObject json = JSONObject.parseObject(body);
            JSONArray rows = json.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) {
                log.info("show channels has no rows, agentNumber:{}", agentNumber);
                return;
            }
            Set<String> matchedUuids = new HashSet<>();
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String uuid = row.getString("uuid");
                if (StringUtils.isNotBlank(uuid) && isCachedAgentChannel(uuid, agentNumber)) {
                    matchedUuids.add(uuid);
                }
            }
            if (matchedUuids.isEmpty()) {
                log.info("no matched agent channels, agentNumber:{}", agentNumber);
                return;
            }

            Set<String> callIds = new HashSet<>();
            matchedUuids.forEach(uuid -> {
                String callId = getUuidVar(address, uuid, "och_call_id");
                if (StringUtils.isNotBlank(callId) && !"_undef_".equalsIgnoreCase(callId)) {
                    callIds.add(callId);
                }
            });

            Set<String> killUuids = new HashSet<>(matchedUuids);
            if (!skipHupall && !callIds.isEmpty()) {
                for (int i = 0; i < rows.size(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String uuid = row.getString("uuid");
                    if (StringUtils.isBlank(uuid)) {
                        continue;
                    }
                    String callId = getUuidVar(address, uuid, "och_call_id");
                    if (StringUtils.isNotBlank(callId) && callIds.contains(callId)) {
                        // 跳过主叫通道(directionType=1)若有流程上下文，保留主叫继续满意度评价
                        CallInfo ciInfo = fsCallCacheService.getCallInfoByUniqueId(uuid);
                        if (ciInfo != null && ciInfo.getFlowDataContext() != null
                                && ciInfo.getChannelMap() != null && ciInfo.getChannelMap().get(uuid) != null
                                && Objects.equals(ciInfo.getChannelMap().get(uuid).getDirectionType(), 1)) {
                            log.info("跳过主叫通道(保留满意度评价) agentNumber:{}, uuid:{}", agentNumber, uuid);
                        } else {
                            killUuids.add(uuid);
                        }
                    }
                }
            }

            killUuids.forEach(uuid -> {
                log.info("kill agent call, agentNumber:{}, uuid:{}", agentNumber, uuid);
                killCall(address, null, uuid);
            });
        } catch (Exception e) {
            log.error("kill agent calls failed, agentNumber:{}, body:{}", agentNumber, body, e);
        }
    }

    private boolean checkSatisfactionFlow(String address, String agentNumber) {
        try {
            EslMessage channelsMsg = sendSyncMsg(address, "show", "channels as json");
            String body = getMessageBody(channelsMsg);
            if (StringUtils.isBlank(body)) {
                return false;
            }
            JSONObject json = JSONObject.parseObject(body);
            JSONArray rows = json.getJSONArray("rows");
            if (rows == null || rows.isEmpty()) {
                return false;
            }
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String uuid = row.getString("uuid");
                if (StringUtils.isBlank(uuid)) {
                    continue;
                }
                CallInfo cachedInfo = fsCallCacheService.getCallInfoByUniqueId(uuid);
                if (cachedInfo != null && cachedInfo.getFlowDataContext() != null
                        && cachedInfo.getChannelMap() != null) {
                    ChannelInfo ci = cachedInfo.getChannelMap().get(uuid);
                    if (ci != null && Objects.equals(ci.getDirectionType(), 2)
                            && agentNumber.equals(ci.getAgentNumber())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("checkSatisfactionFlow error agentNumber:{} err:{}", agentNumber, e.getMessage());
        }
        return false;
    }

    private boolean isAgentChannel(JSONObject row, String agentNumber) {
        for (Object value : row.values()) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value);
            if (StringUtils.equals(text, agentNumber)
                    || StringUtils.contains(text, "/" + agentNumber + "@")
                    || StringUtils.contains(text, "sip:" + agentNumber + "@")
                    || StringUtils.contains(text, "<sip:" + agentNumber + "@")
                    || StringUtils.contains(text, ":" + agentNumber + "@")) {
                return true;
            }
        }
        return false;
    }

    private boolean isCachedAgentChannel(String uniqueId, String agentNumber) {
        CallInfo callInfo = fsCallCacheService.getCallInfoByUniqueId(uniqueId);
        if (callInfo == null || callInfo.getChannelMap() == null) {
            return false;
        }
        ChannelInfo channelInfo = callInfo.getChannelMap().get(uniqueId);
        return channelInfo != null
                && Objects.equals(channelInfo.getDirectionType(), 2)
                && StringUtils.equals(channelInfo.getAgentNumber(), agentNumber);
    }

    private boolean isCallIdChannel(String address, String uuid, String callId) {
        if (StringUtils.equals(getUuidVar(address, uuid, "och_call_id"), callId)
                || StringUtils.equals(getUuidVar(address, uuid, "sip_h_X-OCH-Call-ID"), callId)
                || StringUtils.equals(getUuidVar(address, uuid, "variable_och_call_id"), callId)) {
            return true;
        }
        return false;
    }

    public String getChannelCallId(String address, String uuid) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uuid)) {
            return null;
        }
        try {
            Long cachedCallId = fsCallCacheService == null ? null : fsCallCacheService.getCallId(uuid);
            if (cachedCallId != null) {
                return String.valueOf(cachedCallId);
            }
        } catch (Exception exception) {
            log.debug("Get cached channel call id failed, uuid:{}, error:{}", uuid, exception.getMessage());
        }
        String callId = normalizeUuidVar(getUuidVar(address, uuid, "och_call_id"));
        if (callId == null) {
            callId = normalizeUuidVar(getUuidVar(address, uuid, "sip_h_X-OCH-Call-ID"));
        }
        if (callId == null) {
            callId = normalizeUuidVar(getUuidVar(address, uuid, "variable_och_call_id"));
        }
        return callId;
    }

    private String normalizeUuidVar(String value) {
        String normalized = StringUtils.trimToNull(value);
        return StringUtils.equalsIgnoreCase(normalized, "_undef_") ? null : normalized;
    }

    private String getUuidVar(String address, String uuid, String varName) {
        EslMessage message = sendSyncMsg(address, "uuid_getvar", uuid + EslConstant.SPACE + varName);
        String body = getMessageBody(message).trim();
        if (StringUtils.startsWith(body, "-ERR")) {
            return "";
        }
        return body;
    }

    private String getMessageBody(EslMessage message) {
        if (message == null || message.getBodyLines() == null) {
            return "";
        }
        return String.join("\n", message.getBodyLines());
    }

    public boolean record(String address, Long callId, String uniqueId, String filePath) {
        try {
            if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId) || StringUtils.isBlank(filePath)) {
                log.error("FreeSWITCH启动录音参数不完整 callId:{}, address:{}, uniqueId:{}, record:{}",
                        callId, address, uniqueId, filePath);
                return false;
            }
            sendSyncMsg(address, EslConstant.SETVAR,
                    uniqueId + " record_sample_rate " + StringUtils.defaultIfBlank(sampleRate, "8000"));
            sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " RECORD_STEREO true");
            sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " RECORD_CHECK_BRIDGE true");
            sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " enable_file_write_buffering false");
            sendSyncMsg(address, EslConstant.SETVAR, uniqueId + " recording_follow_transfer true");
            EslMessage response = sendSyncMsg(address, EslConstant.RECORD,
                    uniqueId + " " + EslConstant.START + " " + filePath);
            String responseBody = getMessageBody(response).trim();
            if (response == null || StringUtils.startsWith(responseBody, "-ERR")) {
                log.error("FreeSWITCH启动录音失败 callId:{}, uniqueId:{}, record:{}, response:{}",
                        callId, uniqueId, filePath, responseBody);
                return false;
            }
            log.info("FreeSWITCH启动录音成功 callId:{}, uniqueId:{}, record:{}", callId, uniqueId, filePath);
            return true;
        } catch (Exception e) {
            log.error("FS閻庢鍠掗崑鎾斥攽椤旂⒈鍎忕紓宥呯Ч濡懘宕橀懠顒傦紳闁?callId:{}, uniqueId:{}, ex:{}", callId, uniqueId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 濠碘剝銇滈崕鑼暜?
     *
     * @param address
     * @param callId
     * @param uniqueId
     * @param otherUniqueId
     */
    public void bridgeCall(String address, Long callId, String uniqueId, String otherUniqueId) {
        // Keep FreeSWITCH in the RTP path. The external gateway leg and the WebRTC/SIP
        // agent leg frequently use different codecs and network segments; bypass/proxy
        // media can therefore produce an established call with no audio in either direction.
        prepareBridgeMedia(address, uniqueId);
        prepareBridgeMedia(address, otherUniqueId);
        // 关键修复(线上主叫腿卡死2小时):主叫腿可能仍在播放 IVR/录音提示(luyin.wav 等)。
        // uuid_bridge 对 CS_SOFT_EXECUTE 放音中的腿可能挂起,桥接永远完不成。
        // 桥接前先 break 放音,保证腿回到可桥接状态。
        try {
            sendSyncMsg(address, "uuid_break", uniqueId + " all");
        } catch (Exception e) {
            log.warn("break caller playback before bridge failed (ignored) callId:{} uuid:{}", callId, uniqueId);
        }
        // The caller leg must survive an agent hangup so the IVR can continue to satisfaction.
        sendSyncMsg(address, "uuid_setvar", uniqueId + " hangup_after_bridge false");
        sendSyncMsg(address, "uuid_setvar", uniqueId + " park_after_bridge true");
        sendSyncMsg(address, "uuid_setvar", otherUniqueId + " hangup_after_bridge true");
        log.info("Bridge hangup policy set callId:{}, callerUuid:{}, agentUuid:{}", callId, uniqueId, otherUniqueId);
        sendAsyncMsg(address, EslConstant.BRIDGE, uniqueId + EslConstant.SPACE + otherUniqueId);
    }

    private void prepareBridgeMedia(String address, String uniqueId) {
        sendSyncMsg(address, "uuid_setvar", uniqueId + " bypass_media false");
        sendSyncMsg(address, "uuid_setvar", uniqueId + " proxy_media false");
        sendSyncMsg(address, "uuid_setvar", uniqueId + " media_mix_inbound_outbound_codecs true");
        sendSyncMsg(address, "uuid_setvar", uniqueId + " rtp_rewrite_timestamps true");
        sendSyncMsg(address, "uuid_setvar", uniqueId + " rtp_autoflush_during_bridge true");
    }

    /**
     * update call
     *
     * @param address
     * @param uniqueId
     * @param name
     * @param arg
     */
    public void sendArgs(String address, String uniqueId, String name, String arg) {
        SendMsg msg = new SendMsg(uniqueId);
        msg.addCallCommand(EslConstant.EXECUTE);
        msg.addExecuteAppName(name);
        msg.addExecuteAppArg(arg);
        msg.addGenericLine("async", "true");
        sendMsg(address, msg);
    }


    /**
     * 闂佸憡甯楃粙鎴犵磽閹捐宸濋柣鎾抽瀵?
     *
     * @param callId        闂佸憡绋撻崢褑銇愰埆鏈?
     * @param called        闂佸憡绋撻崢褑銇愰妸鈺佺煑濡炲娴烽崹?
     * @param calledDisplay 闂佸憡绋撻崢褑銇愰妸鈺佸強闁诡垎鍐ㄢ枏
     * @param uniqueId      闂備緡鍋呭畝鍛婄缂嶎敧
     * @param timeOut       闁烩剝甯掗幊蹇擃渻閸岀偛绫嶉柛顐ｆ礃閿?
     * @param callRoute     闁荤姳璀﹂崹鎶藉极鏉堛劎鈹嶉柍鈺佸暕缁?
     */
    public void makeCall(Long callId, String called, String calledDisplay, String uniqueId, Integer timeOut, FsSipGateway callRoute) {
        if (StringUtils.isBlank(called)) {
            log.warn("called:{} is null ", called);
            return;
        }
        String address = RandomUtil.randomEle(new ArrayList<>(fsClientMap.keySet()));
        makeCall(address, callId, called, calledDisplay, uniqueId, timeOut, callRoute);
    }

    public void makeCall(String address, Long callId, String called, String calledDisplay, String uniqueId, Integer timeOut, FsSipGateway callRoute) {
        makeCall(address, callId, called, calledDisplay, uniqueId, timeOut, callRoute, null);
    }

    public void makeCall(String address, Long callId, String called, String calledDisplay, String uniqueId,
                         Integer timeOut, FsSipGateway callRoute, Consumer<String> internalFailureHandler) {
        if (StringUtils.isBlank(called)) {
            log.warn("called:{} is null ", called);
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{return_ring_ready=true").append(",")
                .append("sip_contact_user=").append(calledDisplay).append(",")
                .append("ring_asr=true").append(",")
                .append("fire_asr_events=true").append(",");
        // For internal calls (gatewayType=0), skip absolute_codec_string to let FreeSWITCH
        // auto-negotiate codecs per contact (handles both SIP UDP and WebRTC WebSocket)
        boolean isInternal = callRoute.getGatewayType() != null && callRoute.getGatewayType() == 0;
        if (!isInternal) {
            builder.append("absolute_codec_string=").append(codec).append(",");
        }
        builder.append("origination_caller_id_number=").append(calledDisplay).append(",")
                .append("origination_caller_id_name=").append(calledDisplay);
        // origination_uuid breaks FreeSWITCH multi-contact forking (only calls first contact).
        // Use och_call_id to correlate channel events with our call record instead.
        if (isInternal) {
            builder.append(",").append("och_call_id=").append(callId)
                    .append(",").append("sip_h_X-OCH-Call-ID=").append(callId)
                    .append(",").append("sip_h_X-OCH-Unique-ID=").append(uniqueId);
        } else {
            builder.append(",").append("sip_cid_type=none")
                    .append(",").append("origination_uuid=").append(uniqueId);
        }
        if (timeOut != null) {
            builder.append(",").append("originate_timeout=").append(timeOut);
        }

        builder.append("}");
        // For internal extensions (gatewayType=0), use user/ to look up registered contact directly
        // to avoid FreeSWITCH loop detection when sofia/internal sends INVITE to itself
        if (isInternal) {
            builder.append("user/").append(called);
        } else {
            builder.append("sofia/gateway/")
                    .append(callRoute.getName())
                    .append("/")
                    .append(called);
        }
        builder.append(EslConstant.PARK);

        String originateCmd = builder.toString();
        log.info("fs 闂佸憡鐟﹂崹鍧楀焵椤戣法顦︾紒杈ㄦ尭椤垽濡烽敂鎷樸儵鏌?originate+{}", originateCmd);

        log.info("FreeSWITCH originate callId:{}, gateway:{}, called:{}, command:{}",
                callId, callRoute.getName(), called, originateCmd);
        if (isInternal) {
            originateInternal(address, callId, called, originateCmd, internalFailureHandler);
        } else {
            sendAsyncMsg(address, EslConstant.ORIGINATE, originateCmd);
        }
    }

    public void makeInternalCall(String address, Long callId, String called, String callerDisplay, String uniqueId, Integer timeOut) {
        makeInternalCall(address, callId, called, callerDisplay, uniqueId, timeOut, null);
    }

    public void makeInternalCall(String address, Long callId, String called, String callerDisplay, String uniqueId,
                                 Integer timeOut, Consumer<String> internalFailureHandler) {
        makeInternalCall(address, callId, called, callerDisplay, uniqueId, timeOut,
                internalFailureHandler, false);
    }

    public void makeWebRtcCall(String address, Long callId, String called, String callerDisplay, String uniqueId,
                               Integer timeOut, Consumer<String> internalFailureHandler) {
        makeInternalCall(address, callId, called, callerDisplay, uniqueId, timeOut,
                internalFailureHandler, true);
    }

    private void makeInternalCall(String address, Long callId, String called, String callerDisplay, String uniqueId,
                                  Integer timeOut, Consumer<String> internalFailureHandler,
                                  boolean webRtcOnly) {
        if (StringUtils.isBlank(called)) {
            log.warn("called:{} is null ", called);
            if (internalFailureHandler != null) {
                internalFailureHandler.accept("called number is blank");
            }
            return;
        }

        String contact;
        try {
            contact = webRtcOnly
                    ? resolveWebRtcUserContact(address, called)
                    : resolveUserContact(address, called);
        } catch (RuntimeException exception) {
            log.error("FreeSWITCH contact lookup failed before originate, callId:{}, called:{}",
                    callId, called, exception);
            if (internalFailureHandler != null) {
                internalFailureHandler.accept(StringUtils.defaultIfBlank(
                        exception.getMessage(), "FreeSWITCH contact lookup failed"));
            }
            return;
        }
        if (StringUtils.isBlank(contact)) {
            String failure = (webRtcOnly ? "browser softphone not registered: " : "user not registered: ") + called;
            log.warn("FreeSWITCH internal originate skipped, callId:{}, called:{}, reason:{}",
                    callId, called, failure);
            if (internalFailureHandler != null) {
                internalFailureHandler.accept(failure);
            }
            return;
        }

        StringBuilder builder = new StringBuilder();
        builder.append("{return_ring_ready=true").append(",")
                .append("sip_contact_user=").append(called).append(",")
                .append("ring_asr=true").append(",")
                .append("fire_asr_events=true").append(",")
                // 关键修复(线上坐席不振铃):internal 腿不再裸协商。强制与外部网关腿(默认 PCMA)对齐,
                // 否则主叫 PCMA / 坐席 PCMU 自动协商失败,腿卡 CS_CONSUME_MEDIA 且 codec/ip_addr 为空。
                .append("absolute_codec_string=").append(StringUtils.isNotBlank(internalCodec) ? internalCodec : codec).append(",")
                .append("origination_caller_id_number=").append(callerDisplay).append(",")
                .append("origination_caller_id_name=").append(callerDisplay).append(",")
                .append("och_call_id=").append(callId).append(",")
                .append("sip_h_X-OCH-Call-ID=").append(callId).append(",")
                .append("sip_h_X-OCH-Unique-ID=").append(uniqueId);
        int effectiveTimeout = resolveInternalRingTimeout(timeOut);
        builder.append(",").append("originate_timeout=").append(effectiveTimeout);
        String endpoint = webRtcOnly ? contact : buildInternalDialEndpoint(contact, called);
        builder.append("}").append(endpoint).append(EslConstant.PARK);

        String originateCmd = builder.toString();
        log.info("FreeSWITCH internal originate callId:{}, called:{}, contacts:{}, endpoint:{}, requestedTimeout:{}, effectiveTimeout:{}, command:{}",
                callId, called, contact, endpoint, timeOut, effectiveTimeout, originateCmd);
        originateInternal(address, callId, called, originateCmd, internalFailureHandler);
    }

    int resolveInternalRingTimeout(Integer requestedTimeout) {
        int minimumTimeout = Math.max(internalRingTimeoutSeconds, 1);
        if (requestedTimeout == null) {
            return minimumTimeout;
        }
        return Math.max(requestedTimeout, minimumTimeout);
    }

    String buildInternalDialEndpoint(String contacts, String called) {
        List<String> endpoints = new ArrayList<>();
        if (StringUtils.isNotBlank(contacts)) {
            for (String candidate : contacts.split(",")) {
                String endpoint = StringUtils.trim(candidate);
                boolean supported = StringUtils.startsWithIgnoreCase(endpoint, "sofia/")
                        || StringUtils.startsWithIgnoreCase(endpoint, "verto/");
                if (supported && isUsableContact(endpoint) && !endpoints.contains(endpoint)) {
                    endpoints.add(endpoint);
                }
            }
        }
        return endpoints.isEmpty() ? "user/" + called : String.join(",", endpoints);
    }

    /**
     * Internal extensions may have both a SIP phone and a WebRTC browser registered.
     * FreeSWITCH expands user/&lt;extension&gt; to all active contacts. Run originate in
     * API mode on a dedicated pool so the multi-contact dial string is forked while
     * the ESL event thread remains non-blocking.
     */
    private void originateInternal(String address, Long callId, String called, String originateCmd,
                                   Consumer<String> failureHandler) {
        try {
            originateThreadPool.execute(() -> {
                try {
                    EslMessage response = sendSyncMsg(address, EslConstant.ORIGINATE, originateCmd);
                    String body = getMessageBody(response).trim();
                    if (response == null || !StringUtils.startsWithIgnoreCase(body, "+OK")) {
                        String failure = StringUtils.defaultIfBlank(body, "empty response from FreeSWITCH");
                        log.error("FreeSWITCH internal originate failed, callId:{}, called:{}, response:{}",
                                callId, called, failure);
                        if (failureHandler != null) {
                            failureHandler.accept(failure);
                        }
                    } else {
                        log.info("FreeSWITCH internal originate completed, callId:{}, called:{}, response:{}",
                                callId, called, body);
                    }
                } catch (Exception e) {
                    log.error("FreeSWITCH internal originate exception, callId:{}, called:{}", callId, called, e);
                    if (failureHandler != null) {
                        failureHandler.accept(e.getMessage());
                    }
                }
            });
        } catch (RuntimeException exception) {
            log.error("FreeSWITCH internal originate could not be scheduled, callId:{}, called:{}",
                    callId, called, exception);
            if (failureHandler != null) {
                failureHandler.accept(StringUtils.defaultIfBlank(
                        exception.getMessage(), "FreeSWITCH originate could not be scheduled"));
            }
        }
    }

    public void makeCall(String address, Long callId, String called, String calledDisplay, String uniqueId, Integer timeOut, String gatewayAddress) {
        if (StringUtils.isBlank(called)) {
            log.warn("called:{} is null ", called);
            return;
        }

        StringBuilder builder = new StringBuilder();
        //闂佸吋鍎抽崲鑼躲亹閸モ晜宕夋い鏍ㄧ矋閺嗙姷绱撻崘鈺佺仴闁告鍥ф嵍闁哄瀵х徊?
        String destination = called + Constants.AT + gatewayAddress;
        builder.append("{return_ring_ready=true").append(",")
                .append("sip_contact_user=").append(calledDisplay).append(",")
                .append("ring_asr=true").append(",")
                .append("fire_asr_events=true").append(",")
                .append("absolute_codec_string=").append(codec).append(",")
                .append("origination_caller_id_number=").append(calledDisplay).append(",")
                .append("origination_caller_id_name=").append(calledDisplay).append(",")
                .append("origination_uuid=").append(uniqueId).append(",")
                .append("och_call_id=").append(callId).append(",")
                .append("sip_h_X-OCH-Call-ID=").append(callId).append(",")
                .append("sip_h_X-OCH-Unique-ID=").append(uniqueId);
        if (timeOut != null) {
            builder.append(",").append("originate_timeout=").append(timeOut);
        }
        builder.append("}").append(EslConstant.SOFIA + "/").append(GatewayTypeEnum.EXTERNAL.getDesc()).append("/").append(destination).append(EslConstant.PARK);
        sendAsyncMsg(address, EslConstant.ORIGINATE, builder.toString());
    }

    /**
     * 闂佺顑嗙划宥夘敆濞戙垹缁╁Δ锝呭暟閸?
     *
     * @param address
     * @param uniqueId
     */
    public void playBreak(String address, String uniqueId) {
        SendMsg playback = new SendMsg(uniqueId);
        playback.addCallCommand(EslConstant.EXECUTE);
        playback.addExecuteAppName(EslConstant.BREAK_);
        this.sendMsg(address, playback);
    }

    /**
     * 闂佸湱铏庨崢浠嬪棘娓氣偓濡懘宕楅崨顖ｅ敹
     *
     * @param address
     * @param uniqueId
     * @param file
     */
    public boolean playFile(String address, String uniqueId, String file) {
        SendMsg playfile = new SendMsg(uniqueId);
        playfile.addCallCommand(EslConstant.EXECUTE);
        playfile.addExecuteAppName(EslConstant.PLAYBACK);
        playfile.addExecuteAppArg(file);
        return this.sendMsg(address, playfile);
    }

    public boolean broadcastFile(String address, String uniqueId, String file, String leg) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId) || StringUtils.isBlank(file)) {
            return false;
        }
        String targetLeg = StringUtils.defaultIfBlank(leg, "both").toLowerCase();
        if (!Set.of("aleg", "bleg", "both").contains(targetLeg)) {
            targetLeg = "both";
        }
        try {
            EslMessage response = sendSyncMsg(address, "uuid_broadcast",
                    uniqueId + EslConstant.SPACE + file + EslConstant.SPACE + targetLeg);
            String responseBody = getMessageBody(response).trim();
            if (response == null || StringUtils.startsWithIgnoreCase(responseBody, "-ERR")) {
                log.error("FreeSWITCH桥接提示音播放失败 uniqueId:{}, file:{}, response:{}",
                        uniqueId, file, responseBody);
                return false;
            }
            log.info("FreeSWITCH桥接提示音已下发 uniqueId:{}, file:{}, leg:{}",
                    uniqueId, file, targetLeg);
            return true;
        } catch (Exception exception) {
            log.error("FreeSWITCH桥接提示音播放异常 uniqueId:{}, file:{}",
                    uniqueId, file, exception);
            return false;
        }
    }

    /**
     * 閻庣敻鍋婃禍鐐虹嵁閸℃せ鏋庨柍鈺佸暞濞堝爼鏌熺紒銏犲箺闁哄倷绶氬Λ鍛村礂閸涱垼鍞?
     * @param address 闂侀潻闄勫妯侯焽?
     * @param uniqueId 闂備緡鍋呭畝鍛婄缂嶎敧
     * @param file 闂佸搫鍊稿ú锝呪枎閵忋垺宕夋い鏍ㄦ皑缁?
     * @param number 閻庣敻鍋婃禍鐐虹嵁閸℃せ鏋庨柍鈺佸暞濞?
     */
    public void playFile(String address, String uniqueId, String file, Integer number) {
        SendMsg playfile = new SendMsg(uniqueId);
        playfile.addCallCommand(EslConstant.EXECUTE);
        playfile.addExecuteAppName(EslConstant.LOOP_PLAYBACK);
        playfile.addExecuteAppArg("+"+number+ " "+ file);
        this.sendMsg(address, playfile);
    }

    /**
     * 閻庢鍠掗崑鎾绘煕濮樿鲸鐎盨R
     *
     * @param address
     * @param callId
     * @param uniqueId
     */
    public void detectSpeech(String address, Long callId, String uniqueId) {
        SendMsg speech = new SendMsg(uniqueId);
        speech.addCallCommand(EslConstant.EXECUTE);
        speech.addExecuteAppName(EslConstant.DETECT_SPEECH);
        speech.addExecuteAppArg(EslConstant.UNIMRCP + "aliyun-mrcpserver alimrcp default");
        this.sendMsg(address, speech);
    }

    public void detectSpeechResume(String address, Long callId, String uniqueId) {
        SendMsg speech = new SendMsg(uniqueId);
        speech.addCallCommand(EslConstant.EXECUTE);
        speech.addExecuteAppName(EslConstant.DETECT_SPEECH);
        speech.addExecuteAppArg(EslConstant.RESUME);
        this.sendMsg(address, speech);
    }


    /**
     * <min> <max> <tries> <timeout> <terminators> <file> <invalid_file> [<var_name> [<regexp> [<digit_timeout> [<transfer_on_failure>]]]]
     * play_and_get_digits(301,1 1 1 2000 q /sounds/welcaome.wav silence_stream://250 SYMWRD_DTMF_RETURN [\*0-9#]+ 2000) execute ok
     * 闂佽　鍋撳Δ锝呭暟閸欑偓顨ラ悙璺虹厫闁轰礁缍婂畷?     * @param adress 婵犳鍨辩敮濠勭礊鐎ｎ喖鎹堕柡澶嬪缁?
     * @param uniqueId 闂備緡鍋呭畝鍛婄缂嶎敧
     * @param min 闂佸搫鐗冮崑鎾绘倶韫囨挾绠ｇ紓宥呮嚇瀵偆鏁鍓ь槱闂佸搫鐗冮崑鎾绘倶韫囨挾绠伴柍褜鍓熷Λ璺ㄦ嫻?0闂?     * @param max 闂佸搫鐗冮崑鎾愁熆閸棗鍊荤粔鎾煛娴ｇ绨荤紒杈ㄧ懇瀵敻鍩€椤掍礁绶炵憸宥夊焵椤掑嫭锛熼悹?128闂?     * @param tries 婵犮垽鈧稑宓嗛柣鎾崇埣楠炴﹢顢橀悢鍛婃緬闂佹眹鍔岀€氼剟鎯冮崜褎瀚氶柡鍥╁櫏閸嬔囨煛?     * @param timeout 闂侀潻璐熼崝宥夊几閸愨晝顩烽柤绋跨仛鐏忥繝鏌￠埀顒勫礈瑜忓▔銏ゆ煛婢跺苯鏋庨柟顔筋殜瀹?PAGD 闂佸湱鐟抽崱鈺傛杸闂備焦褰冪粔鐑芥儊椤栨稓鈻曢悗锝庝簻椤ゅ懐绱掑☉姗嗘Ц缂佸墎鍠栭獮蹇涘Ω閵夈儱鈻忛梺鍛婄箓缁夊鑺遍弻銉﹀剭闁告洦鍓﹂崵鎴犵磼婢跺绗氶柡鍡欏枛婵?     * @param terminators 缂傚倷绀侀悧濠囶敆濞戞氨绠?= 婵犵鈧啿鈧綊鎮樻径鎰濠㈣泛顦遍悷鎾绘煟閵娿儱顏柡鍡欏枔閳ь剚绋掗〃鍛存儍椤栨稓顩?<max>闂佹寧绋戦懟顖炲垂椤栫偞鍋ㄩ柕濞垮€楅懝鍓х磽娴ｈ灏版繛纰卞亝濞煎繘骞橀崘鎻掓辈闂佹眹鍔岀€氼參寮抽悢琛″亾濞戞瑣浠犻柍褜鍓欓崐鎼併€呰瀵顭ㄩ崘顏呮疁婵?'=' 閻庢鍠掗崑鎾愁熆閹増褰х紒杈ㄧ箞瀹曟艾鈻庤箛鏇犵暠婵＄偑鍊涢褔鎮洪妸鈺佹嵍闁靛ě鍛厜濠殿喗绺块崹濠氼敄娓氣偓楠炲秴顓奸崼婵嗗箣闂佽浜介崕杈亹閸喐缍囬柟鎯у暱瀵娊鏌ㄥ☉妯煎ⅵ闁逞屽墯閼归箖鎮ラ懜鐢碘枖?'#'闂佹寧绋戦懟顖濄亹閸欏顩烽柕澶堝€涚粈瀣磼瀹€鈧亸銊ф濮樿泛违闁稿本纰嶉煬顒傜磽娴ｅ摜澧涙い鎺撶洴瀵偊寮剁捄銊︽喕闂佸憡鎸哥粔鐟邦焽閺夊３搴ｆ嫚閹绘帩娼?'+' 婵炲濮伴崕閬嶏綖閹邦喚纾奸柛顐ｇ箘濞堟椽鏌涜箛鎾跺煟婵＄虎鍣ｅ畷婵嬫偐閹绘帞鍘?var_name 婵炴垶鎼╅崢鐣屸偓鍨皑閳ь剝顫夊銊モ枔閹寸姷纾奸柟鎯ь嚟娴滎垶鏌涘▎鎰惰€块柛锝嗘そ婵?     * @param file 闂佸湱铏庨崢浠嬪棘娴ｅ湱鐝跺ù鐓庮嚟閸欓箖鏌￠崒姘煑婵炲棎鍨介弫宥囦沪閹呭€掔紓浣插亾闁告繂瀚崵鎴︽煕濞嗘ê浜柍褜鍓欓幊蹇曗偓姘€鍥х闁规儳鐡ㄥ▓鍫曟倵?闂佸湱铏庨崢浠嬪棘娓氣偓瀹曪綁顢涘▎搴ｉ瀺闁荤偞鍑归崑鍡涱敄閸ャ劎鈻旈柍褜鍓氱粙澶愵敂閸涱収浠遍梺鍛婄懇閺€閬嶅汲閻旇　鍋撳☉娆樻畷濠⒀嶇畵瀵剟顢橀妸褏顦╅梺鍛婄懐閸垱绂嶉幒鎾垛枖闁惧繐鍘滈弫鍕煙鐎涙澧卞瑙勫笒閳绘挻绻濋崒婊勬喕缂備焦顨愮紓姘辨啺?闂佺偨鍎茬粭锕妉ence闂?婵炲濮伴崕鍐层€掗悜鑺ュ仼闁靛濡囧暩闂佽鍙庨崹鐣屾濮樿泛违?     * @param invalid_file 閻熸粎澧楅幐濠氬汲閻旇　鍋撳☉娆嶄沪缂?regexp 婵炴垶鎸哥粔瀵镐焊椤曗偓閺屽﹤顓奸崱姗嗘Н闂佸湱铏庨崢浠嬪棘娓氣偓閹啴宕熼顒傜煑闂傚倸锕ㄥ▍鏇㈠几閸愨晝顩烽悹浣告贡缁€鍕煕濞嗘ê鐏熷ù婊勫笚缁嬪鎼归崗澶嬫櫈婵炲濮伴崕鍐层€掗悜鑺ュ仼闁靛濡囧暩闂佽鍙庨崹鐣屾濮樿泛违?     * @param var_name 闁圭厧鐡ㄩ弻銊╂儍閵忋倕瀚夊璺侯儐濞呭繘鏌℃担瑙勭凡闁烩姍鍥х哗闁诡垎鍐ㄦ辈闂佺绻戝﹢褰掓嚈閹达附鍎嶉柛鏇ㄥ灙閸嬫挸顫濋張闈涱棜闂佸憡鐟﹂敃銏ゅ闯濞差亝鏅柛顐ｇ箓鐠佹煡姊洪銏╂缂佽鲸绻冮—鈧俊顖涱儥閸氬洭鏌熼姘櫣闁哥姴鏈粙澶屸偓锝傛櫇閻熸繈鎮规担绋库挃闁汇倕妫欑粋鎺旀媼瀹曞洨协闂佸憡鐟﹂敃銏ゅ闯濞差亜违闁稿本鑹剧紞妤呮偣閸パ冩Щ鐎殿噮鍓涢幉瀛樻媴妞嬪海鎲梻鍌氱墑閸ㄨ鈻撻幋锕€鐏虫繛鎿冩供r_name_invalid闂佺偨鍎茬换鎰?
     * @param regexp 闂佸憡鐗曠紞濠囧储閵堝鏋侀柡澶庢硶閹界喖鏌ｉ妸銉ヮ仾妞ゆ帗绮撳畷姘枎閹哄鍋撻崘鈺傜秶闁诡垎鍛攨闂佹寧绋戦悧鍡氥亹閺屻儲鐒诲鑸电〒缁€澶岀磼鐏炶姤鎯堥柣鈯欏懐绠旈柨鏃囧劵椤╊偊鏌涜箛搴㈢グ妞ゆ柨绻橀獮宥夊焵椤掑嫬瀚夊璺烘捣缂堝鏌涜箛瀣姷缂佽鲸鐟﹂—鈧俊顖涱儥閸氬洭鏌ㄥ☉姗嗘缂佽鲸宀告俊?     * @param digit_timeout 闂佽桨鐒﹀姗€鎮鸿濮婅崵澹曠€ｎ儷銏ゆ煛?闂佽桨鐒﹀姗€鎮鸿缁嬪﹦鈧綆鍠楅敍鐔兼煕韫囧孩绁版い鏂跨箻閹啴宕熼澶婃缂備礁顦扮敮妤呭汲閻斿吋鏅€光偓閸愶絽浜鹃悘鐐跺亹閻熸繈鏌￠崟闈涚仭閻庢皜鍥х闁规儳澧庨惌鎺撴叏濠靛嫬鐏い鎴滅窔瀵偊寮剁捄銊︽喕;闂佸憡甯楁刊浠嬪箵椤忓嫷娼伴柕鍫濇噹婵炲洭鏌ｉ鑽ゎ槮闁诡喗顨婇弫宥団偓娑氭嚑GD 闂佺顑呭ú銈夋偩妤ｅ啫宸濋柣鎾抽瀵ゆ椽鏌￠崒婵愭綈闁汇儱鎳樺鍨緞鐎ｎ偆鍑芥繝銏ｅ煐绾板秹寮抽悢琛″亾濞戞瑯娈旂憸鐗堢叀楠炲繘濡搁敃鈧埅鐢告煥濞戞澧曠憸鐗堢叀閺屽懏寰勫☉姘鳖槷婵帗绋掗…鍫ヮ敇缂佹鈻?<timeout> 闂佹眹鍔岀€氼剟鍩€椤掑倶鈧妲愬璺何?     * @param transfer_on_failure 闁哄鐗婇崕鎶藉春瀹€鍕珘闁逞屽墯瀵板嫯顦查柣銊ュ⒔閹风娀寮撮悤浣镐还闂佽桨鑳舵晶妤€顪冮崒婊€鐒婇柛鈩冾殕閸ゆ垿鏌涘▎妯轰函婵炴潙妫濋獮鎺楀Ψ閵夈儳鍘掓繛杈剧稻濞叉牠藝?
     */
    public void playAndGetDigits(String adress, String uniqueId, Integer min, Integer max, Integer tries, Integer timeout, String terminators, String file, String invalid_file, String var_name, String regexp, Integer digit_timeout, String transfer_on_failure){
        StringBuilder builder = new StringBuilder();
        builder.append(min).append(EslConstant.SPACE)
                .append(max).append(EslConstant.SPACE)
                .append(tries).append(EslConstant.SPACE)
                .append(timeout).append(EslConstant.SPACE)
                .append(terminators).append(EslConstant.SPACE)
                .append(file).append(EslConstant.SPACE)
                .append(invalid_file).append(EslConstant.SPACE)
                .append(var_name).append(EslConstant.SPACE)
                .append(regexp).append(EslConstant.SPACE);
        if(StringUtils.isNotBlank(transfer_on_failure)){
            builder.append(transfer_on_failure);
        }
        this.sendArgs(adress, uniqueId,EslConstant.PLAY_AND_GET_DIGITS, builder.toString());
    }

    /**
     * 闂佽　鍋撳Δ锝呭暟閸欓箖鎮归崶褎顥滈柛?
     * @param address
     * @param uniqueId
     * @param content
     */
    public void playAndDetectSpeech(String address, String uniqueId, String content) {
        SendMsg speech = new SendMsg(uniqueId);
        speech.addCallCommand(EslConstant.EXECUTE);
    }

    /**
     * 启动通话音频推流（方案 7.2 / 11.2 P0）。
     *
     * <p>保留旧签名供现有调用方兼容，默认使用 {@code mod_audio_fork}。
     * 新代码应传入明确模块，避免探测 {@code mod_audio_stream} 却仍调用
     * {@code uuid_audio_fork}。任何异常仅记日志，绝不影响通话（方案 7.15）。</p>
     *
     * @param address    FreeSWITCH ESL 地址
     * @param callId     业务呼叫 ID（写入 channel var，供推流端回填 Header）
     * @param uniqueId   通道 UUID
     * @param streamUrl  WebSocket 推流目标（ws:// 或 wss://）
     * @param sampleRate 采样率，默认 8000
     * @param mixType    mono|mixed|stereo，默认 mono
     * @return true 表示命令已下发且未立即返回 -ERR
     */
    public boolean startAudioStream(String address, Long callId, String uniqueId, String streamUrl,
                                    int sampleRate, String mixType) {
        return startAudioStream(address, callId, uniqueId, FsAudioStreamModule.AUDIO_FORK.configuredName(),
                streamUrl, sampleRate, mixType);
    }

    /**
     * Start a channel audio stream with the selected, explicitly supported module.
     */
    public boolean startAudioStream(String address, Long callId, String uniqueId, String module,
                                    String streamUrl, int sampleRate, String mixType) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId) || StringUtils.isBlank(streamUrl)) {
            log.warn("startAudioStream 参数不足 address={} uuid={} url={}", address, uniqueId, streamUrl);
            return false;
        }
        FsAudioStreamModule selected = FsAudioStreamModule.resolve(module);
        if (selected == null) {
            log.warn("startAudioStream 不支持的模块 module={} callId={} uuid={}", module, callId, uniqueId);
            return false;
        }
        if (!selected.supportsEndpoint(streamUrl)) {
            log.warn("startAudioStream 端点协议不兼容 module={} transport={} callId={} uuid={}",
                    selected.configuredName(), FsAudioStreamModule.endpointTransport(streamUrl), callId, uniqueId);
            return false;
        }
        String mix = StringUtils.isBlank(mixType) ? "mono" : mixType.trim();
        int rate = sampleRate <= 0 ? 8000 : sampleRate;
        try {
            // 供模块/适配器读取并关联业务呼叫。
            if (callId != null) {
                sendSyncMsg(address, "uuid_setvar", uniqueId + " och_call_id " + callId);
                sendSyncMsg(address, "uuid_setvar", uniqueId + " och_asr_stream 1");
            }
            // uuid_audio_(fork|stream) <uuid> start <ws-url> <mix-type> <sampling-rate>
            String args = uniqueId + " start " + streamUrl + " " + mix + " " + rate;
            EslMessage resp = sendSyncMsg(address, selected.apiCommand(), args);
            String body = getMessageBody(resp);
            if (body != null && body.contains("-ERR")) {
                log.warn("FS 音频推流启动失败 module:{} callId:{} uuid:{} body:{}",
                        selected.configuredName(), callId, uniqueId, body);
                return false;
            }
            log.info("FS 音频推流命令成功 module:{} callId:{} uuid:{} rate:{} mix:{}",
                    selected.configuredName(), callId, uniqueId, rate, mix);
            return true;
        } catch (Exception e) {
            log.error("启动音频推流异常(忽略，不影响通话) module:{} callId:{} uuid:{} err:{}",
                    selected.configuredName(), callId, uniqueId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 停止通话音频推流。
     */
    public boolean stopAudioStream(String address, Long callId, String uniqueId) {
        return stopAudioStream(address, callId, uniqueId, FsAudioStreamModule.AUDIO_FORK.configuredName());
    }

    public boolean stopAudioStream(String address, Long callId, String uniqueId, String module) {
        if (StringUtils.isBlank(address) || StringUtils.isBlank(uniqueId)) {
            return false;
        }
        FsAudioStreamModule selected = FsAudioStreamModule.resolve(module);
        if (selected == null) {
            log.warn("stopAudioStream 不支持的模块 module={} callId={} uuid={}", module, callId, uniqueId);
            return false;
        }
        try {
            EslMessage resp = sendSyncMsg(address, selected.apiCommand(), uniqueId + " stop");
            String body = getMessageBody(resp);
            if (body != null && body.contains("-ERR")) {
                log.warn("FS 音频推流停止失败 module:{} callId:{} uuid:{} body:{}",
                        selected.configuredName(), callId, uniqueId, body);
                return false;
            }
            sendSyncMsg(address, "uuid_setvar", uniqueId + " och_asr_stream 0");
            log.info("FS 音频推流已停止 module:{} callId:{} uuid:{}",
                    selected.configuredName(), callId, uniqueId);
            return true;
        } catch (Exception e) {
            log.error("停止音频推流异常(忽略) module:{} callId:{} uuid:{} err:{}",
                    selected.configuredName(), callId, uniqueId, e.getMessage());
            return false;
        }
    }

}
