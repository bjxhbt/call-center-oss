package com.och.ai.tts.provider;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TencentTtsProviderTest {

    @Test
    void splitText_prefersPunctuationAndRespectsLimit() {
        String text = "国家科技管理信息公共服务平台如遇到系统问题请拨打电话进行咨询，"
                + "重听请按井号键，返回请按星号键，人工服务请按零。"
                + "这是第二段较长的业务说明，用于验证长文本可以连续合成而不会被截断。";

        List<String> segments = TencentTtsProvider.splitText(text, 40);

        assertThat(segments).hasSizeGreaterThan(1);
        assertThat(segments).allMatch(segment -> segment.length() <= 40);
        assertThat(String.join("", segments)).isEqualTo(text);
    }
}
