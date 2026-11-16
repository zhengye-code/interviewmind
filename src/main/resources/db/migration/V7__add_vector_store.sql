CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS vector_store (
                                            id         uuid DEFAULT gen_random_uuid() PRIMARY KEY,
                                            content    text,
                                            metadata   json,
                                            embedding  vector(1024)
);

-- 近似最近邻索引：数据多了之后检索才不会全表扫描
CREATE INDEX IF NOT EXISTS idx_vector_store_embedding
    ON vector_store USING hnsw (embedding vector_cosine_ops);