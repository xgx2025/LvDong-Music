-- MySQL 8 增量迁移。先在选定数据库执行预检；不删除或改写任何历史记录。
-- 请在暂停收藏写入的维护窗口执行；MySQL DDL 会隐式提交，不能依靠事务回滚 DDL。
SELECT song_id, user_id, COUNT(*) AS duplicate_count
FROM song_likes GROUP BY song_id, user_id HAVING COUNT(*) > 1;

DELIMITER $$
DROP PROCEDURE IF EXISTS migrate_song_likes_unique_002$$
CREATE PROCEDURE migrate_song_likes_unique_002()
BEGIN
    IF EXISTS (SELECT 1 FROM song_likes GROUP BY song_id, user_id HAVING COUNT(*) > 1) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Duplicate song likes found; review manually before migration';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'song_likes'
                     AND index_name = 'uk_song_likes_song_user') THEN
        ALTER TABLE song_likes ADD UNIQUE INDEX uk_song_likes_song_user (song_id, user_id);
    END IF;
END$$
DELIMITER ;
CALL migrate_song_likes_unique_002();
DROP PROCEDURE migrate_song_likes_unique_002;

-- 只读核对计数偏差，人工确认修复方案后再更新。
SELECT s.id, s.favorite_count, COUNT(sl.id) AS actual_count
FROM songs s LEFT JOIN song_likes sl ON sl.song_id = s.id
GROUP BY s.id, s.favorite_count HAVING s.favorite_count <> COUNT(sl.id);
