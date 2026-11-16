package com.interviewmind.modules.interview.model.status;

public enum InterviewSessionStatus {
    /** 进行中：还有题没答 */
    IN_PROGRESS,
    /** 已结束：全部答完 */
    COMPLETED,
    /** 已放弃：用户主动退出 */
    ABORTED
}