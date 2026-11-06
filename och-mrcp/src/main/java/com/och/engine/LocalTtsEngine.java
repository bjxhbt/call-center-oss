package com.och.engine;

import com.alibaba.fastjson.JSONObject;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地离线 TTS 引擎(内网/无外网环境使用)
 *
 * 原理: 调用本地命令行 TTS 工具合成语音, 生成 8kHz 单声道 PCM 裸数据,
 *       直接送入 MRCP RTP 通道, 不依赖任何云服务/外网。
 *
 * 支持的本地 TTS 工具(任选其一, 通过环境变量 LOCAL_TTS_CMD 配置):
 *   - ekho:  ekho -s 8000 -o output.wav "要说的话"
 *   - piper: echo "要说的话" | piper -m zh_CN-huayan-medium -r 8000 -f output.wav
 *   - festival: echo "要说的话" | text2wave -o output.wav
 *
 * 配置(engine.conf 或环境变量):
 *   LOCAL_TTS_CMD  = TTS 命令模板, {TEXT}=文本占位符, {OUT}=输出wav路径
 *                    例如: ekho -s 8000 -o "{OUT}" "{TEXT}"
 *   未配置时默认: ekho -s 8000 -o "{OUT}" "{TEXT}"
 *
 * 音频格式要求: 8kHz 16bit 单声道 PCM(与云引擎输出一致)
 */
@Slf4j
public class LocalTtsEngine implements TtsEngine {

    /** 命令模板, {TEXT} 和 {OUT} 为占位符 */
    private final String cmdTemplate;

    /** 临时目录 */
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", "/tmp");

    public LocalTtsEngine() {
        // 优先环境变量, 其次 engine.conf(通过 CloudConfigManager), 最后默认 ekho
        String cmd = System.getenv("LOCAL_TTS_CMD");
        if (cmd == null || cmd.trim().isEmpty()) {
            try {
                com.och.engine.CloudConfig cfg = CloudConfigManager.getConfig("local");
                if (cfg != null && cfg.getApiKey() != null && !cfg.getApiKey().isEmpty()) {
                    cmd = cfg.getApiKey(); // 复用 apiKey 字段存命令模板
                }
            } catch (Exception ignored) {
            }
        }
        if (cmd == null || cmd.trim().isEmpty()) {
            cmd = "ekho -s 8000 -o \"{OUT}\" \"{TEXT}\"";
        }
        this.cmdTemplate = cmd;
        log.info("LocalTtsEngine 初始化, cmdTemplate={}", cmdTemplate);
    }

    @Override
    public void synthesize(String text, String voice, TtsCallback callback) {
        if (text == null || text.isEmpty()) {
            callback.onComplete((byte[]) null, new IllegalArgumentException("合成文本不能为空"));
            return;
        }
        try {
            byte[] pcm = doSynthesize(text);
            log.info("本地TTS合成成功, 文本长度={}, PCM字节数={}", text.length(), pcm.length);
            callback.onComplete(pcm, null);

            JSONObject result = new JSONObject();
            result.put("status", 200);
            result.put("statusText", "success");
            result.put("sessionId", IdUtil.fastSimpleUUID());
            callback.onComplete(result);
        } catch (Exception e) {
            log.error("本地TTS合成失败: {}", e.getMessage(), e);
            callback.onComplete((byte[]) null, e);
        }
    }

    /**
     * 调用本地 TTS 工具合成, 返回 8kHz PCM
     */
    private byte[] doSynthesize(String text) throws Exception {
        String outFile = TMP_DIR + File.separator + "tts_" + IdUtil.fastSimpleUUID() + ".wav";
        String cmd = cmdTemplate
                .replace("{OUT}", outFile)
                .replace("{TEXT}", text.replace("\"", ""));

        log.info("本地TTS执行命令: {}", cmd);

        ProcessBuilder pb;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            pb = new ProcessBuilder("cmd.exe", "/c", cmd);
        } else {
            pb = new ProcessBuilder("sh", "-c", cmd);
        }
        pb.redirectErrorStream(true);
        Process proc = pb.start();

        // 读输出流(防止进程因缓冲区满而阻塞)
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try (InputStream is = proc.getInputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                stdout.write(buf, 0, n);
            }
        }

        int code = proc.waitFor();
        if (code != 0) {
            String err = stdout.toString("GBK").trim();
            throw new IllegalStateException("本地TTS命令退出码=" + code + ", 输出: " + err);
        }

        // 读取生成的 wav 文件, 提取 PCM 裸数据(跳过44字节wav头)
        Path wavPath = Paths.get(outFile);
        if (!Files.exists(wavPath)) {
            throw new IllegalStateException("本地TTS未生成输出文件: " + outFile);
        }
        byte[] wavBytes = Files.readAllBytes(wavPath);
        try {
            Files.deleteIfExists(wavPath);
        } catch (Exception ignored) {
        }

        byte[] pcm;
        if (wavBytes.length > 44 && wavBytes[0] == 'R' && wavBytes[1] == 'I' && wavBytes[2] == 'F' && wavBytes[3] == 'F') {
            // 标准 WAV: 跳过44字节头, 取 PCM 数据
            pcm = new byte[wavBytes.length - 44];
            System.arraycopy(wavBytes, 44, pcm, 0, pcm.length);
        } else {
            // 已经是裸 PCM
            pcm = wavBytes;
        }

        if (pcm.length == 0) {
            throw new IllegalStateException("本地TTS合成音频为空");
        }
        return pcm;
    }
}