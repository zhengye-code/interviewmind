package com.interviewmind.modules.interview.model.status;

public enum KnowledgeImportStatus {
    /** 正在写入向量 */
    IMPORTING,
    /** 已就绪，可以检索 */
    READY,
    /** 写入失败，重试会覆盖 */
    FAILED
}
