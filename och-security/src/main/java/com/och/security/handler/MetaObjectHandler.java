package com.och.security.handler;

import com.och.security.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * @author danmo
 * @description 填充器
 * @date 2023/6/19 21:42
 **/
@Slf4j
@Component
public class MetaObjectHandler implements com.baomidou.mybatisplus.core.handlers.MetaObjectHandler {
    private static final String CREATE_TIME = "createTime";
    private static final String CREATE_BY = "createBy";
    private static final String UPDATE_TIME = "updateTime";
    private static final String UPDATE_BY = "updateBy";
    private static final String DEL_FLAG = "delFlag";

    @Override
    public void insertFill(MetaObject metaObject) {
        //创建时间
        this.setFieldValByName(CREATE_TIME, new Date(), metaObject);
        this.setFieldValByName(UPDATE_TIME, new Date(), metaObject);
        //创建人
        Long userId = getUserId();

        if(StringUtils.isEmpty((String) this.getFieldValByName(CREATE_BY, metaObject))){
            this.setFieldValByName(CREATE_BY, userId, metaObject);
        }

        if(StringUtils.isEmpty((String) this.getFieldValByName(UPDATE_BY, metaObject))){
            this.setFieldValByName(UPDATE_BY, userId, metaObject);
        }

        //删除标识默认0
        if (this.getFieldValByName(DEL_FLAG, metaObject) == null) {
            this.setFieldValByName(DEL_FLAG, 0, metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        //更新时间
        this.setFieldValByName(UPDATE_TIME, new Date(), metaObject);
        //更新人
        this.setFieldValByName(UPDATE_BY, getUserId(), metaObject);
    }


    private Long getUserId() {
        Long userId;
        try {
            userId = SecurityUtils.getUserId();
        } catch (RuntimeException e){
            userId = 1L;
        }
        return userId;
    }
}
