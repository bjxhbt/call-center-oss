package com.och.ai.asr.eval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 转写效果量化指标工具（方案 7.16）：字符错误率(CER) 与 关键词识别率。
 *
 * <p>纯 Java 实现，与 {@code docs/asr/FunASR_verify.py} 的 cer() 口径一致，
 * 供平台对持久化的最终文本与参考文本做批量评估，无需 Python 环境。</p>
 */
public final class CerCalculator {

    private CerCalculator() {
    }

    /**
     * 字符级错误率：Levenshtein(ref, hyp) / max(len(ref), 1)。
     *
     * @param reference  参考文本（人工标注）
     * @param hypothesis 识别最终文本
     * @return 0.0~1.0+（完全一致为 0.0；参考为空且假设非空为 1.0）
     */
    public static double cer(String reference, String hypothesis) {
        if (reference == null) {
            reference = "";
        }
        if (hypothesis == null) {
            hypothesis = "";
        }
        char[] r = reference.toCharArray();
        char[] h = hypothesis.toCharArray();
        if (r.length == 0) {
            return h.length == 0 ? 0.0 : 1.0;
        }
        int[] prev = new int[h.length + 1];
        for (int j = 0; j <= h.length; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= r.length; i++) {
            int[] cur = new int[h.length + 1];
            cur[0] = i;
            for (int j = 1; j <= h.length; j++) {
                int cost = r[i - 1] == h[j - 1] ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[h.length] / (double) r.length;
    }

    /**
     * 关键词识别率：识别文本中命中的关键词数 / 关键词总数（大小写不敏感，子串匹配）。
     *
     * @param hypothesis 识别最终文本
     * @param keywords   关键词集合
     * @return 0.0~1.0（无关键词时返回 1.0）
     */
    public static double keywordHitRate(String hypothesis, Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 1.0;
        }
        String low = hypothesis == null ? "" : hypothesis.toLowerCase();
        int hit = 0;
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && low.contains(kw.toLowerCase())) {
                hit++;
            }
        }
        return hit / (double) keywords.size();
    }

    /**
     * 列出未命中的关键词（排障用）。
     */
    public static List<String> missedKeywords(String hypothesis, Collection<String> keywords) {
        List<String> missed = new ArrayList<>();
        if (keywords == null) {
            return missed;
        }
        String low = hypothesis == null ? "" : hypothesis.toLowerCase();
        for (String kw : keywords) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            if (!low.contains(kw.toLowerCase())) {
                missed.add(kw);
            }
        }
        return missed;
    }
}
