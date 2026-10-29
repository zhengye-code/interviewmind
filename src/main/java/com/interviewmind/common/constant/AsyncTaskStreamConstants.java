package com.interviewmind.common.constant;

public class AsyncTaskStreamConstants {
    private AsyncTaskStreamConstants() {}
    // ===== 通用消息字段 =====
    public static final String FIELD_RETRY_COUNT = "retryCount";

    // ===== 简历分析 =====
    public static final String RESUME_ANALYZE_STREAM_KEY = "resume:analyze:stream";
    public static final String RESUME_ANALYZE_GROUP_NAME = "analyze-group";
    public static final String RESUME_ANALYZE_CONSUMER_PREFIX = "analyze-consumer-";
    public static final String FIELD_RESUME_ID = "resumeId";

    // ===== 消费参数 =====
    /** 每次最多领取几条 */
    public static final int BATCH_SIZE = 1;
    /** 没有消息时阻塞等待多少秒（避免空转烧 CPU） */
    public static final long BLOCK_SECONDS = 5L;
    /** 出错后的重试间隔（毫秒） */
    public static final long ERROR_SLEEP_MS = 1000L;
}
