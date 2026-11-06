package com.och.system.constant;

/**
 * 统一业务标识规范（任务 1.2）。
 *
 * <p>定义全链路使用的业务标识字段及其用途、生成规则与所属表，供五个实施方案共用。
 * 任何模块不得自行新增含义冲突的同名字段；如需扩展只能向后兼容追加可选字段。</p>
 *
 * <h3>标识清单</h3>
 * <table border="1">
 * <tr><th>字段</th><th>类型/格式</th><th>用途</th><th>所在实体/表</th><th>生成方</th></tr>
 * <tr><td>{@link #CALL_ID}</td><td>Long(雪花ID)</td><td>一次业务呼叫的唯一标识，贯穿话单/录音/转写</td><td>CallInfo / call_record.call_id</td><td>ICallService.makeCall 雪花生成</td></tr>
 * <tr><td>{@link #FS_UNIQUE_ID}</td><td>String(32位)</td><td>FreeSWITCH 单个通道的唯一标识 UUID</td><td>ChannelInfo / ChannelInfo.uniqueId</td><td>发起通道时随机生成</td></tr>
 * <tr><td>{@link #AGENT_ID}</td><td>Long</td><td>坐席主键，绑定 SIP 账号与系统用户</td><td>SipAgent.id / sip_agent</td><td>坐席管理后台分配</td></tr>
 * <tr><td>{@link #GATEWAY_ID}</td><td>Long</td><td>SIP/E1 出局网关主键</td><td>FsSipGateway.id / fs_sip_gateway</td><td>网关管理分配</td></tr>
 * <tr><td>{@link #ROUTE_ID}</td><td>Long</td><td>呼叫路由规则主键</td><td>CallRoute.id / call_route</td><td>路由管理分配</td></tr>
 * <tr><td>{@link #RECORD_ID}</td><td>Long</td><td>录音文件记录主键</td><td>SysFile/VoiceFile.id</td><td>文件上传服务分配</td></tr>
 * </table>
 *
 * <h3>一次业务呼叫与多个 FreeSWITCH 通道的关系</h3>
 * <ul>
 *   <li>一个 {@code callId} 对应一次业务呼叫（如：坐席1000 呼出 / 外线呼入到坐席）；</li>
 *   <li>一次业务呼叫在 FreeSWITCH 侧由 1~N 个通道组成（坐席侧通道 + E1/外线侧通道 + IVR/转接通道），
 *       每个通道拥有独立的 {@code uniqueId}；</li>
 *   <li>{@code CallInfo.uniqueIdList} 与 {@code CallInfo.channelMap(uniqueId -&gt; ChannelInfo)}
 *       维护“一业务呼叫 -&gt; 多通道”映射，所有 ESL 事件通过 {@code uniqueId} 反查 {@code callId}
 *       归属业务，再据此做幂等与状态合并（见 9.8）。</li>
 * </ul>
 *
 * @author och
 */
public final class BusinessIdentifiers {

    private BusinessIdentifiers() {
    }

    /** 业务呼叫唯一标识字段名。 */
    public static final String CALL_ID = "callId";

    /** FreeSWITCH 通道唯一标识字段名。 */
    public static final String FS_UNIQUE_ID = "uniqueId";

    /** 坐席主键字段名。 */
    public static final String AGENT_ID = "agentId";

    /** SIP/E1 网关主键字段名。 */
    public static final String GATEWAY_ID = "gatewayId";

    /** 呼叫路由主键字段名。 */
    public static final String ROUTE_ID = "routeId";

    /** 录音文件记录主键字段名。 */
    public static final String RECORD_ID = "recordId";

    /** FreeSWITCH 通道 UUID 的标准长度。 */
    public static final int FS_UNIQUE_ID_LENGTH = 32;

    /** 坐席 SIP 账号（agentNumber）允许的最大长度。 */
    public static final int AGENT_NUMBER_MAX_LENGTH = 32;

    /** 业务呼叫 ID 前缀（仅用于日志/缓存 key 可读性，不影响雪花 ID 数值）。 */
    public static final String CALL_ID_PREFIX = "call:";
}
