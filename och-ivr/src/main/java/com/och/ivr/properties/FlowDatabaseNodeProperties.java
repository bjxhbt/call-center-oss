package com.och.ivr.properties;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据库节点属性
 * 对应原系统 database 节点：执行SQL并将结果列写入流程变量
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class FlowDatabaseNodeProperties extends FlowNodeProperties {

    /**
     * JDBC 驱动
     */
    private String driver;

    /**
     * 数据库IP
     */
    private String ip;

    /**
     * 端口
     */
    private String port;

    /**
     * 库名/schema
     */
    private String schema;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * SQL语句，支持 ${var} 引用流程变量；结果集第一行的列名(别名)会写入同名流程变量
     */
    private String query;

    /**
     * 下一步事件，默认 next
     */
    private String event;
}
