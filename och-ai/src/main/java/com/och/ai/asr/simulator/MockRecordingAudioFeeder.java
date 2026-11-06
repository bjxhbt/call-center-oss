package com.och.ai.asr.simulator;

import com.och.ai.asr.model.AudioCodec;
import com.och.ai.asr.model.CallAudioFrame;
import com.och.ai.asr.model.Speaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

/**
 * 录音切片模拟器（方案 7.2 / 并行启动）：把本地 WAV/PCM 录音切成 {@link CallAudioFrame}，
 * 模拟 FreeSWITCH 实时音频流，使 ASR 服务层可<b>独立</b>于真实呼入呼出链路联调与测试。
 *
 * <p>用法：</p>
 * <pre>
 *   feeder.feed("/data/call.wav", 1001L, "uuid-x", 20, true, frame -&gt; ingest.onFrame(frame));
 * </pre>
 *
 * <p>解析最小 RIFF/WAVE：定位 {@code fmt }(采样率/位深/声道) 与 {@code data}(PCM)；
 * 仅支持 16bit 线性 PCM WAV（电话录音常见）。裸 PCM 可用 {@link #feedRawPcm}。</p>
 */
@Slf4j
@Component
public class MockRecordingAudioFeeder {

    /**
     * 切片并投递一个 WAV 录音。
     *
     * @param wavPath        WAV 文件路径（classpath: 前缀或绝对路径）
     * @param callId         业务 callId
     * @param uniqueId       通道 UUID
     * @param frameDurationMs 每帧时长
     * @param realtime       是否按帧间隔实时投递（测试传 false 立即投递）
     * @param intervalMs     实时模式帧间隔
     * @param speaker        说话人
     * @param sink           帧消费者
     * @return 已投递帧数
     */
    public int feed(String wavPath, Long callId, String uniqueId, int frameDurationMs,
                    boolean realtime, long intervalMs, Speaker speaker, Consumer<CallAudioFrame> sink) throws IOException {
        byte[] wav = readAll(wavPath);
        WavInfo info = parseWav(wav);
        return feedBytes(info.pcm, info.sampleRate, info.channels, callId, uniqueId,
                frameDurationMs, realtime, intervalMs, speaker, sink);
    }

    /**
     * 投递裸 16bit 线性 PCM（小端），用于测试无 WAV 头的场景。
     */
    public int feedRawPcm(byte[] pcm, int sampleRate, int channels, Long callId, String uniqueId,
                          int frameDurationMs, boolean realtime, long intervalMs, Speaker speaker,
                          Consumer<CallAudioFrame> sink) {
        return feedBytes(pcm, sampleRate, channels, callId, uniqueId, frameDurationMs, realtime, intervalMs, speaker, sink);
    }

    private int feedBytes(byte[] pcm, int sampleRate, int channels, Long callId, String uniqueId,
                          int frameDurationMs, boolean realtime, long intervalMs, Speaker speaker,
                          Consumer<CallAudioFrame> sink) {
        int bytesPerSample = 2;
        int frameBytes = Math.max(1, sampleRate * channels * bytesPerSample * frameDurationMs / 1000);
        int offset = 0;
        long seq = 0;
        int sent = 0;
        long startTs = System.currentTimeMillis();
        while (offset < pcm.length) {
            int end = Math.min(offset + frameBytes, pcm.length);
            int len = end - offset;
            // 不足半帧的尾部合并到上一帧，避免极短尾帧
            byte[] chunk = new byte[len];
            System.arraycopy(pcm, offset, chunk, 0, len);
            CallAudioFrame frame = CallAudioFrame.builder()
                    .callId(callId)
                    .uniqueId(uniqueId)
                    .codec(AudioCodec.PCM)
                    .sampleRate(sampleRate)
                    .channels(channels)
                    .data(chunk)
                    .speaker(speaker)
                    .sequence(seq)
                    .timestampMs(startTs + seq * frameDurationMs)
                    .silent(false)
                    .build();
            sink.accept(frame);
            sent++;
            seq++;
            offset = end;
            if (realtime && intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        log.info("录音切片投递完成 callId:{} frames:{} durationMs≈{}", callId, sent, sent * frameDurationMs);
        return sent;
    }

    private byte[] readAll(String wavPath) throws IOException {
        if (wavPath == null || wavPath.isBlank()) {
            throw new IOException("wavPath 为空");
        }
        if (wavPath.startsWith("classpath:")) {
            String res = wavPath.substring("classpath:".length());
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(res)) {
                if (in == null) {
                    throw new IOException("classpath 资源不存在: " + res);
                }
                return readAllBytes(in);
            }
        }
        Path path = Paths.get(wavPath);
        return Files.readAllBytes(path);
    }

    private byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * 解析 WAV：定位 fmt 与 data。仅支持 PCM(格式码 1)、16bit。
     */
    WavInfo parseWav(byte[] wav) throws IOException {
        if (wav == null || wav.length < 44) {
            throw new IOException("非法 WAV（长度不足）");
        }
        if (!"RIFF".equals(readAscii(wav, 0, 4)) || !"WAVE".equals(readAscii(wav, 8, 4))) {
            throw new IOException("非 RIFF/WAVE 文件");
        }
        int sampleRate = 0;
        short channels = 0;
        short bitsPerSample = 0;
        byte[] data = null;
        int pos = 12;
        while (pos + 8 <= wav.length) {
            String chunkId = readAscii(wav, pos, 4);
            int size = readLeInt(wav, pos + 4);
            int dataStart = pos + 8;
            if ("fmt ".equals(chunkId)) {
                channels = readLeShort(wav, dataStart + 2);
                sampleRate = readLeInt(wav, dataStart + 4);
                bitsPerSample = readLeShort(wav, dataStart + 14);
            } else if ("data".equals(chunkId)) {
                int len = Math.min(size, wav.length - dataStart);
                data = new byte[len];
                System.arraycopy(wav, dataStart, data, 0, len);
            }
            pos = dataStart + size + (size % 2); // 奇数字节补齐
        }
        if (sampleRate == 0 || data == null) {
            throw new IOException("WAV 缺少 fmt/data");
        }
        if (bitsPerSample != 16) {
            throw new IOException("仅支持 16bit PCM WAV，当前 bits=" + bitsPerSample);
        }
        return new WavInfo(data, sampleRate, channels);
    }

    private String readAscii(byte[] b, int off, int len) {
        return new String(b, off, len);
    }

    private int readLeInt(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private short readLeShort(byte[] b, int off) {
        return ByteBuffer.wrap(b, off, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    /** WAV 解析结果。 */
    record WavInfo(byte[] pcm, int sampleRate, short channels) {
    }
}
