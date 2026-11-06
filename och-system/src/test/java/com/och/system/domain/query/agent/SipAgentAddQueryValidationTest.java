package com.och.system.domain.query.agent;

import com.och.system.constant.ErrorCode;
import com.och.system.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 坐席新增/修改参数校验单元测试（任务 4.1）。
 *
 * @author och
 */
class SipAgentAddQueryValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void validQueryPasses() {
        SipAgentAddQuery q = new SipAgentAddQuery();
        q.setName("坐席A");
        q.setUserId(1L);
        q.setAgentNumber("1000");
        Set<ConstraintViolation<SipAgentAddQuery>> violations = validator.validate(q);
        assertThat(violations).isEmpty();
    }

    @Test
    void blankNameRejected() {
        SipAgentAddQuery q = valid();
        q.setName("");
        assertThat(validator.validate(q)).anyMatch(v -> v.getMessage().contains("坐席名称"));
    }

    @Test
    void invalidAgentNumberRejected() {
        SipAgentAddQuery q = valid();
        q.setAgentNumber("100abc");
        assertThat(validator.validate(q)).anyMatch(v -> v.getMessage().contains("号码"));
    }

    @Test
    void nullUserIdRejected() {
        SipAgentAddQuery q = valid();
        q.setUserId(null);
        assertThat(validator.validate(q)).anyMatch(v -> v.getMessage().contains("员工ID"));
    }

    @Test
    void businessException_carriesErrorCodeAndField() {
        // 验证 SIP 重复异常携带字段级错误码（任务 10.2 字段级提示契约）
        BusinessException ex = new BusinessException(ErrorCode.AGENT_SIP_BIND_REPEAT, "agentNumber");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.AGENT_SIP_BIND_REPEAT.getCode());
        assertThat(ex.getField()).isEqualTo("agentNumber");
        assertThat(ex.getMessage()).contains("SIP账号");
    }

    private SipAgentAddQuery valid() {
        SipAgentAddQuery q = new SipAgentAddQuery();
        q.setName("坐席A");
        q.setUserId(1L);
        q.setAgentNumber("1000");
        return q;
    }
}
