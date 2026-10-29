package com.interviewmind.modules.resume.listener;

import com.interviewmind.modules.resume.service.ResumeAnalyzeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 回归测试：锁住消费者的生命周期接线。
 *
 * 背景：曾经出现过 start()/stop()/isRunning() 三个方法体全为空的情况——
 * 编译通过、启动无报错、上传成功，但消费者从未运行，状态永远停在 PENDING。
 * 这类"静默失效"必须靠测试兜住，而不是靠肉眼。
 */
class ResumeAnalyzeConsumerLifecycleTest {

    @Test
    @DisplayName("start() 后 isRunning 为 true，stop() 后为 false")
    void 生命周期状态正确切换() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> streamOps = mock(StreamOperations.class);

        when(template.<String, String>opsForStream()).thenReturn(streamOps);
        when(streamOps.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        // read 未打桩 → 返回 null，消费循环走 continue 分支，不会阻塞

        ResumeAnalyzeService service = mock(ResumeAnalyzeService.class);
        ResumeAnalyzeConsumer consumer = new ResumeAnalyzeConsumer(template, service,
                mock(ResumeAnalyzeProducer.class));

        assertThat(consumer.isRunning()).isFalse();

        consumer.start();
        assertThat(consumer.isRunning()).isTrue();

        consumer.stop();
        assertThat(consumer.isRunning()).isFalse();
    }

    @Test
    @DisplayName("重复调用 stop() 不抛异常")
    void 重复停止安全() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> streamOps = mock(StreamOperations.class);

        when(template.<String, String>opsForStream()).thenReturn(streamOps);
        when(streamOps.createGroup(anyString(), any(), anyString())).thenReturn("OK");

        ResumeAnalyzeConsumer consumer = new ResumeAnalyzeConsumer(template,
                mock(ResumeAnalyzeService.class), mock(ResumeAnalyzeProducer.class));

        consumer.start();
        consumer.stop();
        consumer.stop();

        assertThat(consumer.isRunning()).isFalse();
    }
}
