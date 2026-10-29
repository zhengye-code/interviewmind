-- V3: 异步任务恢复所需字段
ALTER TABLE resumes ADD COLUMN analyze_updated_at TIMESTAMP(6);
ALTER TABLE resumes ADD COLUMN analyze_recovery_count INTEGER NOT NULL DEFAULT 0;

-- 历史数据回填：避免恢复调度器把老数据误判为「卡住」
UPDATE resumes SET analyze_updated_at = uploaded_at WHERE analyze_updated_at IS NULL;