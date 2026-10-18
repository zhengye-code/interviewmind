-- 容器首次初始化时自动执行（对照参考项目 docker/postgres/init.sql）
-- 目的：让全新的数据卷里也带 pgvector 扩展，避免 M5 向量检索时才报
--       ERROR: type "vector" does not exist
CREATE EXTENSION IF NOT EXISTS vector;
