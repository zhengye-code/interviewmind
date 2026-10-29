package com.interviewmind.modules.resume.listener;

import com.interviewmind.common.constant.AsyncTaskStreamConstants;
import com.interviewmind.modules.resume.service.ResumeAnalyzeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.async.resume-analyze.enabled",
        havingValue = "true", matchIfMissing = true)
public class ResumeAnalyzeConsumer implements SmartLifecycle {

    private final ResumeAnalyzeService resumeAnalyzeService;
    private final ResumeAnalyzeProducer resumeAnalyzeProducer;
    /**
     * 显式声明三个泛型参数。
     * StringRedisTemplate.opsForStream() 是 <HK, HV> 泛型方法，
     * 直接链式调用 .read(...) 时 Java 推断不出类型，会退化成 Object。
     * 先赋给带明确类型的变量，推断就成立了。
     */
    private final StreamOperations<String, String, String> streamOps;

    /** 每个实例一个唯一消费者名，多实例部署时互不干扰 */
    private final String consumerName =
            AsyncTaskStreamConstants.RESUME_ANALYZE_CONSUMER_PREFIX + UUID.randomUUID();

    private volatile boolean running = false;
    private Thread workerThread;

    public ResumeAnalyzeConsumer(StringRedisTemplate redisTemplate,
                                 ResumeAnalyzeService resumeAnalyzeService, ResumeAnalyzeProducer resumeAnalyzeProducer) {
        this.resumeAnalyzeService = resumeAnalyzeService;
        this.streamOps = redisTemplate.opsForStream();
        this.resumeAnalyzeProducer = resumeAnalyzeProducer;
    }

    @Override
    public void start() {
        ensureConsumerGroup();
        running = true;
        workerThread = Thread.ofPlatform()
                .name("resume-analyze-consumer")
                .daemon(false)
                .start(this::consumeLoop);
        log.info("简历分析消费者已启动: consumer={}", consumerName);
    }

    @Override
    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }
        log.info("简历分析消费者已停止: consumer={}", consumerName);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 创建消费者组；已存在时会抛 BUSYGROUP，属正常情况 */
    private void ensureConsumerGroup() {
        try {
            streamOps.createGroup(
                    AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                    ReadOffset.from("0"),
                    AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME);
            log.info("消费者组已创建: {}", AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME);
        } catch (Exception e) {
            log.info("消费者组已存在，跳过创建: {}", e.getMessage());
        }
    }

    private void consumeLoop() {
        while (running) {
            try {
                List<MapRecord<String, String, String>> records = streamOps.read(
                        Consumer.from(AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME, consumerName),
                        StreamReadOptions.empty()
                                .count(AsyncTaskStreamConstants.BATCH_SIZE)
                                .block(Duration.ofSeconds(AsyncTaskStreamConstants.BLOCK_SECONDS)),
                        StreamOffset.create(AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                                ReadOffset.lastConsumed()));

                // 注意顺序：先判 null 再 isEmpty，否则 null 时直接 NPE
                if (records == null || records.isEmpty()) {
                    continue;
                }
                for (MapRecord<String, String, String> record : records) {
                    handle(record);
                }
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.error("简历分析消费循环异常，{}ms 后重试",
                        AsyncTaskStreamConstants.ERROR_SLEEP_MS, e);
                sleepQuietly();
            }
        }
    }

    private void handle(MapRecord<String, String, String> record) {
        String rawResumeId = record.getValue().get(AsyncTaskStreamConstants.FIELD_RESUME_ID);
        String rawRetry=record.getValue().get(AsyncTaskStreamConstants.FIELD_RETRY_COUNT);
        int retryCount=parseRetryCount(rawRetry);
        try {
            Long resumeId = Long.valueOf(rawResumeId);
            log.info("开始处理简历分析任务: resumeId={},retryCount={}, recordId={}", resumeId,retryCount, record.getId());
            resumeAnalyzeService.analyzeIfNeeded(resumeId);
            log.info("简历分析任务处理完成: resumeId={}", resumeId);
        } catch (Exception e) {
            // 失败时异常对象必须作为最后一个参数传入，否则日志里没有堆栈
            if(retryCount<AsyncTaskStreamConstants.MAX_RETRY_COUNT) {
                int next=retryCount+1;
                Long resumeId = Long.valueOf(rawResumeId);
                resumeAnalyzeService.markPendingForRetry(resumeId);
                resumeAnalyzeProducer.enqueue(resumeId, next);
                log.warn("任务已重新入队,resumeId={},下次 retryCount={}",resumeId,next);
            }else{
                log.error("任务已超过最大重试次数，标记为FAILED resumeId={}",rawResumeId);
            }

        } finally {
            acknowledge(record);
        }
    }

    private void acknowledge(MapRecord<String, String, String> record) {
        try {
            streamOps.acknowledge(
                    AsyncTaskStreamConstants.RESUME_ANALYZE_STREAM_KEY,
                    AsyncTaskStreamConstants.RESUME_ANALYZE_GROUP_NAME,
                    record.getId());
        } catch (Exception e) {
            log.error("ACK 失败: recordId={}", record.getId(), e);
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(AsyncTaskStreamConstants.ERROR_SLEEP_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private int parseRetryCount(String rawRetryCount){
        if(rawRetryCount==null || rawRetryCount.isEmpty()){
            return 0;
        }
        try {
            return Integer.valueOf(rawRetryCount);
        }catch (Exception e){
            return 0;
        }
    }
}
