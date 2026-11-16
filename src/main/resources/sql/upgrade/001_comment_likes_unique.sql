-- ============================================================
-- 升级脚本 001：comment_likes 增加唯一索引
-- 目的：保证“一个用户对一条评论只能点赞一次”，让点赞接口可以幂等执行，
--       避免并发下重复插入导致 like_count 被重复累加。
-- 适用：已存在的 lvdong_music_db（新建库直接用 lvdong_music_db.sql 即可）
-- 幂等：可重复执行
-- ============================================================

USE `lvdong_music_db`;

-- 1. 清理历史重复数据：同一 (comment_id, user_id) 只保留一条
DELETE cl FROM `comment_likes` cl
JOIN (
    SELECT `comment_id`, `user_id`, MIN(`id`) AS keep_id
    FROM `comment_likes`
    GROUP BY `comment_id`, `user_id`
    HAVING COUNT(*) > 1
) dup
  ON cl.`comment_id` = dup.`comment_id`
 AND cl.`user_id` = dup.`user_id`
WHERE cl.`id` <> dup.keep_id;

-- 2. 重建 like_count，使其与 comment_likes 真实行数一致
UPDATE `comments` c
SET c.`like_count` = (
    SELECT COUNT(*) FROM `comment_likes` cl WHERE cl.`comment_id` = c.`id`
);

-- 3. 加唯一索引（已存在则跳过）
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'comment_likes'
      AND index_name = 'uk_comment_user'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE `comment_likes` ADD UNIQUE INDEX `uk_comment_user` (`comment_id`, `user_id`)',
    'SELECT ''uk_comment_user already exists'' AS msg');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
