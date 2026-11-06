package com.och.api.controller.fs;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.och.common.base.BaseController;
import com.och.common.base.ResResult;
import com.och.common.config.redis.RedisService;
import com.och.common.constant.CacheConstants;
import com.och.esl.client.FsClient;
import com.och.system.domain.entity.CallRecord;
import com.och.system.domain.entity.SipAgent;
import com.och.system.domain.vo.agent.SipAgentStatusVo;
import com.och.system.service.ICallRecordService;
import com.och.system.service.ISipAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实时监控接口
 *
 * <p>口径修正(首页统计不准根因):
 * <ol>
 *   <li>话单(call_record)只在<b>最后一腿挂断</b>时落库——进行中通话不在表里,
 *       "当前通话"必须查 FreeSWITCH 实时通道,查 call_end_time IS NULL 恒为 0;</li>
 *   <li>call_state 是挂断原因(1=正常挂机)不是接通标识——接通判定改用 answer_time IS NOT NULL;</li>
 *   <li>坐席真实状态在 Redis(fs:agent:status,路由/挂断链路实时维护),
 *       sip_agent.online_status 是滞后的展示字段——统计改读 Redis。</li>
 * </ol></p>
 */
@Slf4j
@Tag(name = "实时监控")
@RequestMapping("/v1/monitor")
@RestController
public class MonitorController extends BaseController {

    private static final int AGENT_OFFLINE_STATUS = 4;

    @Autowired
    private ICallRecordService callRecordService;

    @Autowired
    private ISipAgentService sipAgentService;

    @Autowired
    private RedisService redisService;

    @Autowired
    private FsClient fsClient;

    @Value("${freeswitch.stale-channel-timeout-seconds:21600}")
    private long staleChannelTimeoutSeconds;

    @Operation(description = "获取监控概览(口径修正版)")
    @GetMapping("/overview")
    public ResResult<Map<String, Object>> getOverview() {
        try {
            Map<String, Object> data = new HashMap<>();
            Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());

            // 今日通话总数:以 call_start_time 计(话单挂断才落库,进行中通话单独由 activeCalls 实时返回)
            QueryWrapper<CallRecord> todayWrapper = new QueryWrapper<>();
            todayWrapper.ge("call_start_time", todayStart);
            long todayTotal = callRecordService.count(todayWrapper);
            data.put("todayTotal", todayTotal);

            // 今日接通数:answer_time IS NOT NULL(接通事实),不用 call_state(那是挂断原因)
            QueryWrapper<CallRecord> answerWrapper = new QueryWrapper<>();
            answerWrapper.ge("call_start_time", todayStart).isNotNull("answer_time");
            long todayAnswered = callRecordService.count(answerWrapper);
            data.put("todayAnswered", todayAnswered);

            // 今日接通率
            double answerRate = todayTotal > 0 ? (double) todayAnswered / todayTotal * 100 : 0;
            data.put("answerRate", Math.round(answerRate * 100.0) / 100.0);

            // 今日呼入/呼出:direction 取 DirectionEnum.getType() 值(1=呼入 INBOUND, 2=呼出 OUTBOUND)。
            // 注意 CallRecord 实体上的 "1-呼出 2-呼入" 注释是错的,实际落库为 DirectionEnum 值。
            QueryWrapper<CallRecord> inboundWrapper = new QueryWrapper<>();
            inboundWrapper.ge("call_start_time", todayStart).eq("direction", 1);
            data.put("todayInbound", callRecordService.count(inboundWrapper));

            QueryWrapper<CallRecord> outboundWrapper = new QueryWrapper<>();
            outboundWrapper.ge("call_start_time", todayStart).eq("direction", 2);
            data.put("todayOutbound", callRecordService.count(outboundWrapper));

            // 当前通话:FreeSWITCH 实时通道数(话单挂断才落库,不能从 call_record 查)
            data.put("activeCalls", countActiveCalls());

            // 坐席统计:一个 SIP 号码(agentNumber)= 一个坐席,不因多台 IP 电话/多个注册终端重复计数。
            // DB 仅取启用坐席全集后按 agentNumber 去重,再用 FreeSWITCH 注册状态校准 Redis。
            List<SipAgent> activeAgents = listActiveAgents();
            List<SipAgent> distinctAgents = distinctByAgentNumber(activeAgents);
            data.put("totalAgents", distinctAgents.size());

            String fsAddress = fsClient.anyAddress();
            Map<String, Boolean> registrationCache = new HashMap<>();
            Map<Integer, Long> statusMap = distinctAgents.stream()
                    .map(agent -> liveAgentStatus(agent, fsAddress, registrationCache))
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            long onlineAgents = distinctAgents.size() - statusMap.getOrDefault(AGENT_OFFLINE_STATUS, 0L);
            data.put("onlineAgents", onlineAgents);
            data.put("idleAgents", statusMap.getOrDefault(1, 0L));
            data.put("busyAgents", statusMap.getOrDefault(2, 0L));
            data.put("dndAgents", statusMap.getOrDefault(3, 0L));
            data.put("offlineAgents", statusMap.getOrDefault(AGENT_OFFLINE_STATUS, 0L));
            data.put("onCallAgents", statusMap.getOrDefault(5, 0L));
            data.put("ringingAgents", statusMap.getOrDefault(6, 0L));
            data.put("wrapUpAgents", statusMap.getOrDefault(7, 0L));

            return success(data);
        } catch (Exception e) {
            log.error("获取监控概览失败: {}", e.getMessage(), e);
            return ResResult.error("获取监控概览失败: " + e.getMessage());
        }
    }

    /**
     * 当前通话数:以"通话"为单位去重,而非通道(腿)数。
     * <ul>
     *   <li>业务通话:所有腿共享 och_call_id,按 och_call_id 去重;</li>
     *   <li>未桥接的呼入(振铃/排队):主叫腿 direction=inbound,按 uuid 计;</li>
     *   <li>未桥接的坐席 fork 腿(direction=outbound)不单独计数,避免双注册多腿虚高。</li>
     * </ul>
     */
    private long countActiveCalls() {
        return listLiveCalls().size();
    }

    /**
     * 坐席实时状态:以 FreeSWITCH 是否注册作为在线底线,Redis 只提供已注册坐席的细分状态。
     */
    private Integer liveAgentStatus(SipAgent agent) {
        return liveAgentStatus(agent, null, new HashMap<>());
    }

    private Integer liveAgentStatus(SipAgent agent, String fsAddress,
                                    Map<String, Boolean> registrationCache) {
        Integer cachedStatus = cachedAgentStatus(agent);
        String agentNumber = StringUtils.trimToNull(agent.getAgentNumber());
        if (StringUtils.isBlank(fsAddress) || StringUtils.isBlank(agentNumber)) {
            return cachedStatus == null ? AGENT_OFFLINE_STATUS : cachedStatus;
        }
        boolean registered = registrationCache.computeIfAbsent(agentNumber,
                number -> fsClient.isUserRegistered(fsAddress, number));
        if (!registered) {
            return AGENT_OFFLINE_STATUS;
        }
        // 注册但 Redis 尚无状态时，按空闲处理；状态 4 可能是掉线后的旧缓存，不能覆盖真实注册状态。
        return cachedStatus == null || Objects.equals(cachedStatus, AGENT_OFFLINE_STATUS)
                ? 1 : cachedStatus;
    }

    private Integer cachedAgentStatus(SipAgent agent) {
        try {
            Object cached = redisService.getCacheMapValue(
                    CacheConstants.AGENT_CURRENT_STATUS_KEY, String.valueOf(agent.getId()));
            if (cached instanceof SipAgentStatusVo vo && vo.getStatus() != null) {
                return vo.getStatus();
            }
        } catch (Exception e) {
            log.warn("monitor overview: read agent status failed, agentId:{} err:{}",
                    agent.getId(), e.getMessage());
        }
        return null;
    }

    List<SipAgent> listActiveAgents() {
        return sipAgentService.list(new QueryWrapper<SipAgent>()
                        .eq("status", 1)
                        .eq("del_flag", 0)).stream()
                .filter(agent -> Objects.equals(agent.getStatus(), 1)
                        && Objects.equals(agent.getDelFlag(), 0))
                .toList();
    }

    /**
     * 按 agentNumber 去重:一个 SIP 号码 = 一个坐席,不因多台 IP 电话/多账号行重复计数。
     * 同号码多行时保留"非离线(在线/忙/通话)"的那一行,避免统计成离线。
     */
    private List<SipAgent> distinctByAgentNumber(List<SipAgent> agents) {
        Map<String, SipAgent> byNumber = new LinkedHashMap<>();
        for (SipAgent agent : agents) {
            String number = StringUtils.isNotBlank(agent.getAgentNumber())
                    ? agent.getAgentNumber() : ("id:" + agent.getId());
            byNumber.merge(number, agent, (old, cur) ->
                    liveAgentStatus(cur) != 4 ? cur : old);
        }
        return new ArrayList<>(byNumber.values());
    }

    @Operation(description = "获取坐席状态列表(实时:Redis + FS 注册校准,按 SIP 号码去重)")
    @GetMapping("/agents")
    public ResResult<List<Map<String, Object>>> getAgentStatusList() {
        try {
            List<SipAgent> agents = listActiveAgents();
            String address = fsClient.anyAddress();
            Map<String, Boolean> registrationCache = new HashMap<>();

            List<Map<String, Object>> result = new ArrayList<>();
            for (SipAgent agent : distinctByAgentNumber(agents)) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", agent.getId());
                map.put("name", agent.getName());
                map.put("agentNumber", agent.getAgentNumber());
                map.put("onlineStatus", liveAgentStatus(agent, address, registrationCache));
                result.add(map);
            }
            return success(result);
        } catch (Exception e) {
            log.error("获取坐席状态列表失败: {}", e.getMessage(), e);
            return ResResult.error("获取坐席状态列表失败: " + e.getMessage());
        }
    }

    @Operation(description = "获取正在进行的通话列表(实时:FS 通道)")
    @GetMapping("/active-calls")
    public ResResult<Map<String, Object>> getActiveCalls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            List<Map<String, Object>> live = listLiveCalls();
            long total = live.size();
            int fromIndex = Math.min((page - 1) * size, live.size());
            int toIndex = Math.min(fromIndex + size, live.size());

            Map<String, Object> data = new HashMap<>();
            data.put("list", live.subList(fromIndex, toIndex));
            data.put("total", total);
            data.put("page", page);
            data.put("size", size);
            return success(data);
        } catch (Exception e) {
            log.error("获取进行中通话列表失败: {}", e.getMessage(), e);
            return ResResult.error("获取进行中通话列表失败: " + e.getMessage());
        }
    }

    /**
     * 实时通话:FreeSWITCH show channels,以"通话"为单位去重(非通道/腿)。
     * 前端字段契约保持(callId/callerNumber/calleeNumber/direction/callStartTime)。
     */
    List<Map<String, Object>> listLiveCalls() {
        Map<String, Map<String, Object>> calls = new LinkedHashMap<>();
        try {
            String address = fsClient.anyAddress();
            if (StringUtils.isBlank(address)) {
                return new ArrayList<>();
            }
            String body = fsClient.sendApiText(address, "show", "channels as json");
            if (StringUtils.isBlank(body)) {
                return new ArrayList<>();
            }
            JSONObject json = JSONObject.parseObject(body);
            var rows = json.getJSONArray("rows");
            if (rows == null) {
                return new ArrayList<>();
            }
            List<LiveChannel> channels = new ArrayList<>();
            Set<String> businessCallIds = new HashSet<>();
            long nowEpoch = System.currentTimeMillis() / 1000L;
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!isLiveChannel(row) || isStaleChannel(row, nowEpoch)) {
                    continue;
                }
                String uuid = row.getString("uuid");
                if (StringUtils.isBlank(uuid)) {
                    continue;
                }
                String businessCallId = firstNotBlank(
                        row.getString("och_call_id"),
                        row.getString("variable_och_call_id"),
                        fsClient.getChannelCallId(address, uuid));
                channels.add(new LiveChannel(row, businessCallId));
                if (StringUtils.isNotBlank(businessCallId)) {
                    businessCallIds.add(businessCallId);
                }
            }

            Map<String, String> channelRoots = buildChannelRoots(channels);
            Map<String, String> componentBusinessCallIds = new HashMap<>();
            for (LiveChannel channel : channels) {
                if (StringUtils.isNotBlank(channel.businessCallId())) {
                    componentBusinessCallIds.putIfAbsent(
                            channelRoots.get(channel.row().getString("uuid")), channel.businessCallId());
                }
            }
            Set<String> endedCallIds = loadEndedCallIds(businessCallIds);
            for (LiveChannel channel : channels) {
                JSONObject row = channel.row();
                String uuid = row.getString("uuid");
                String rootUuid = channelRoots.get(uuid);
                String businessCallId = firstNotBlank(
                        channel.businessCallId(), componentBusinessCallIds.get(rootUuid));
                if (StringUtils.isNotBlank(businessCallId) && endedCallIds.contains(businessCallId)) {
                    continue;
                }
                String direction = row.getString("direction");
                String key;
                if (StringUtils.isNotBlank(businessCallId)) {
                    key = "b:" + businessCallId;
                } else if ("inbound".equalsIgnoreCase(direction)) {
                    key = "i:" + rootUuid;
                } else {
                    continue;
                }
                if (calls.containsKey(key) && !"inbound".equalsIgnoreCase(direction)) {
                    continue;
                }
                Map<String, Object> map = new HashMap<>();
                map.put("callId", StringUtils.defaultIfBlank(businessCallId, uuid));
                map.put("callerNumber", row.getString("cid_num"));
                map.put("calleeNumber", StringUtils.defaultIfBlank(row.getString("initial_dest"), row.getString("dest")));
                map.put("direction", "inbound".equalsIgnoreCase(direction) ? 1 : 2);
                map.put("callStartTime", row.getString("created"));
                calls.put(key, map);
            }
        } catch (Exception e) {
            log.warn("list live calls failed: {}", e.getMessage());
        }
        List<Map<String, Object>> list = new ArrayList<>(calls.values());
        list.sort((a, b) -> String.valueOf(b.get("callStartTime")).compareTo(String.valueOf(a.get("callStartTime"))));
        return list;
    }

    private Map<String, String> buildChannelRoots(List<LiveChannel> channels) {
        Map<String, String> parents = new HashMap<>();
        for (LiveChannel channel : channels) {
            String uuid = channel.row().getString("uuid");
            parents.put(uuid, uuid);
        }
        for (LiveChannel channel : channels) {
            String uuid = channel.row().getString("uuid");
            for (String field : List.of(
                    "call_uuid", "b_uuid", "b_call_uuid", "bridge_uuid", "other_leg_uuid",
                    "variable_bridge_uuid", "variable_other_loopback_leg_uuid",
                    "variable_origination_uuid", "variable_signal_bond", "variable_originate_signal_bond")) {
                String linkedUuid = StringUtils.trimToNull(channel.row().getString(field));
                if (linkedUuid != null && parents.containsKey(linkedUuid)) {
                    unionChannelRoots(parents, uuid, linkedUuid);
                }
            }
        }
        Map<String, String> roots = new HashMap<>();
        for (String uuid : parents.keySet()) {
            roots.put(uuid, findChannelRoot(parents, uuid));
        }
        return roots;
    }

    private void unionChannelRoots(Map<String, String> parents, String firstUuid, String secondUuid) {
        String firstRoot = findChannelRoot(parents, firstUuid);
        String secondRoot = findChannelRoot(parents, secondUuid);
        if (!StringUtils.equals(firstRoot, secondRoot)) {
            parents.put(secondRoot, firstRoot);
        }
    }

    private String findChannelRoot(Map<String, String> parents, String uuid) {
        String parent = parents.get(uuid);
        if (StringUtils.equals(parent, uuid)) {
            return uuid;
        }
        String root = findChannelRoot(parents, parent);
        parents.put(uuid, root);
        return root;
    }

    private Set<String> loadEndedCallIds(Set<String> businessCallIds) {
        if (businessCallIds.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            QueryWrapper<CallRecord> wrapper = new QueryWrapper<>();
            wrapper.select("call_id").in("call_id", businessCallIds).isNotNull("call_end_time");
            return callRecordService.list(wrapper).stream()
                    .map(CallRecord::getCallId)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("load ended call ids failed, keep FreeSWITCH live result: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private record LiveChannel(JSONObject row, String businessCallId) {
    }

    private boolean isLiveChannel(JSONObject row) {
        if (row == null || StringUtils.isBlank(row.getString("uuid"))) {
            return false;
        }
        return !isTerminalChannelState(row.getString("state"))
                && !isTerminalChannelState(row.getString("callstate"));
    }

    private boolean isStaleChannel(JSONObject row, long nowEpoch) {
        if (staleChannelTimeoutSeconds <= 0) {
            return false;
        }
        try {
            long createdEpoch = row.getLongValue("created_epoch");
            return createdEpoch > 0 && nowEpoch - createdEpoch > staleChannelTimeoutSeconds;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTerminalChannelState(String state) {
        if (StringUtils.isBlank(state)) {
            return false;
        }
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        return "HANGUP".equals(normalized)
                || "HANGUP_COMPLETE".equals(normalized)
                || "CS_HANGUP".equals(normalized)
                || "CS_REPORTING".equals(normalized)
                || "CS_DESTROY".equals(normalized)
                || "CS_DONE".equals(normalized);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    @Operation(description = "获取最近通话记录(分页)")
    @GetMapping("/recent-calls")
    public ResResult<Map<String, Object>> getRecentCalls(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            QueryWrapper<CallRecord> countWrapper = new QueryWrapper<>();
            countWrapper.isNotNull("call_end_time");
            long total = callRecordService.count(countWrapper);

            QueryWrapper<CallRecord> wrapper = new QueryWrapper<>();
            wrapper.isNotNull("call_end_time").orderByDesc("call_start_time");
            int offset = (page - 1) * size;
            wrapper.last("LIMIT " + size + " OFFSET " + offset);
            List<CallRecord> records = callRecordService.list(wrapper);

            List<Map<String, Object>> list = new ArrayList<>();
            for (CallRecord record : records) {
                Map<String, Object> map = new HashMap<>();
                map.put("callId", record.getCallId());
                map.put("callerNumber", record.getCallerNumber());
                map.put("calleeNumber", record.getCalleeNumber());
                map.put("direction", record.getDirection());
                map.put("agentName", record.getAgentName());
                map.put("callState", record.getCallState());
                map.put("callStartTime", record.getCallStartTime());
                map.put("callEndTime", record.getCallEndTime());
                map.put("answerFlag", record.getAnswerFlag());
                map.put("hangupDir", record.getHangupDir());
                if (record.getAnswerTime() != null && record.getCallEndTime() != null) {
                    long sec = (record.getCallEndTime().getTime() - record.getAnswerTime().getTime()) / 1000;
                    map.put("callDuration", sec);
                } else {
                    map.put("callDuration", 0);
                }
                list.add(map);
            }
            Map<String, Object> data = new HashMap<>();
            data.put("list", list);
            data.put("total", total);
            data.put("page", page);
            data.put("size", size);
            return success(data);
        } catch (Exception e) {
            log.error("获取最近通话记录失败: {}", e.getMessage(), e);
            return ResResult.error("获取最近通话记录失败: " + e.getMessage());
        }
    }

    @Operation(description = "获取小时级通话趋势")
    @GetMapping("/hourly-trend")
    public ResResult<List<Map<String, Object>>> getHourlyTrend() {
        try {
            Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
            QueryWrapper<CallRecord> wrapper = new QueryWrapper<>();
            wrapper.ge("call_start_time", todayStart).orderByAsc("call_start_time");
            List<CallRecord> records = callRecordService.list(wrapper);

            // 按小时分组统计
            Map<Integer, Long> totalByHour = new TreeMap<>();
            Map<Integer, Long> answeredByHour = new TreeMap<>();
            for (int i = 0; i < 24; i++) {
                totalByHour.put(i, 0L);
                answeredByHour.put(i, 0L);
            }

            Calendar cal = Calendar.getInstance();
            for (CallRecord record : records) {
                if (record.getCallStartTime() != null) {
                    cal.setTime(record.getCallStartTime());
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    totalByHour.merge(hour, 1L, Long::sum);
                    if (record.getAnswerTime() != null) {
                        answeredByHour.merge(hour, 1L, Long::sum);
                    }
                }
            }

            List<Map<String, Object>> result = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("hour", String.format("%02d:00", i));
                item.put("total", totalByHour.get(i));
                item.put("answered", answeredByHour.get(i));
                result.add(item);
            }
            return success(result);
        } catch (Exception e) {
            log.error("获取小时级趋势失败: {}", e.getMessage(), e);
            return ResResult.error("获取小时级趋势失败: " + e.getMessage());
        }
    }
}
