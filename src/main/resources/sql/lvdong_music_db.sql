/*
 Navicat Premium Data Transfer

 Source Server         : mysql
 Source Server Type    : MySQL
 Source Server Version : 80041
 Source Host           : localhost:3306
 Source Schema         : lvdong_music_db

 Target Server Type    : MySQL
 Target Server Version : 80041
 File Encoding         : 65001

 Date: 30/05/2026 15:29:56
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for albums
-- ----------------------------
DROP TABLE IF EXISTS `albums`;
CREATE TABLE `albums`  (
  `id` bigint NOT NULL COMMENT '专辑ID',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '专辑名称',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for comment_likes
-- ----------------------------
DROP TABLE IF EXISTS `comment_likes`;
CREATE TABLE `comment_likes`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `comment_id` bigint NOT NULL COMMENT '评论ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_comment_user`(`comment_id` ASC, `user_id` ASC) USING BTREE COMMENT '同一用户对同一评论只能点赞一次'
) ENGINE = InnoDB AUTO_INCREMENT = 114 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for comments
-- ----------------------------
DROP TABLE IF EXISTS `comments`;
CREATE TABLE `comments`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '评论ID，主键',
  `user_id` bigint NOT NULL COMMENT '评论者ID',
  `resource_type` tinyint NOT NULL COMMENT '资源类型：1-歌曲，2-专辑，3-歌单',
  `resource_id` bigint NOT NULL COMMENT '资源ID（歌曲、专辑、歌单ID）',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '评论内容',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父评论ID，0表示主评论，非0表示回复',
  `reply_to_user_id` bigint NULL DEFAULT 0 COMMENT '回复的目标用户ID(0表示不是回复)',
  `like_count` int NULL DEFAULT 0 COMMENT '点赞数',
  `reply_count` int NULL DEFAULT 0 COMMENT '回复数（对主评论有效）',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1-正常，0-删除',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user`(`user_id` ASC) USING BTREE COMMENT '用户查询索引',
  INDEX `idx_resource`(`resource_id` ASC) USING BTREE COMMENT '资源查询索引',
  INDEX `idx_parent`(`parent_id` ASC) USING BTREE COMMENT '回复关系索引',
  INDEX `idx_created`(`created_at` ASC) USING BTREE COMMENT '时间排序索引',
  INDEX `idx_reply_to`(`reply_to_user_id` ASC) USING BTREE COMMENT '回复目标索引',
  INDEX `idx_hot`(`like_count` ASC, `created_at` ASC) USING BTREE COMMENT '热门评论索引'
) ENGINE = InnoDB AUTO_INCREMENT = 143 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for playlists
-- ----------------------------
DROP TABLE IF EXISTS `playlists`;
CREATE TABLE `playlists`  (
  `id` bigint NOT NULL COMMENT '歌单ID',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '歌单名',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for singers
-- ----------------------------
DROP TABLE IF EXISTS `singers`;
CREATE TABLE `singers`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '与用户id相同',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '与用户头像相同，不要添加，id.png',
  `intro` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `gender` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `nationality` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '国籍',
  `status` tinyint NULL DEFAULT NULL COMMENT '0-申请阶段，1-已认证，申请失败直接删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 732765394795892737 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for song_likes
-- ----------------------------
DROP TABLE IF EXISTS `song_likes`;
CREATE TABLE `song_likes`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `song_id` bigint NOT NULL COMMENT '歌曲ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `created_at` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for song_plays
-- ----------------------------
DROP TABLE IF EXISTS `song_plays`;
CREATE TABLE `song_plays`  (
  `play_id` bigint NOT NULL COMMENT '主键',
  `song_id` bigint NOT NULL COMMENT '歌曲ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `play_duration` int NOT NULL COMMENT '实际播放时长（秒）',
  `play_time` datetime NOT NULL COMMENT '播放时间',
  PRIMARY KEY (`play_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for song_singer
-- ----------------------------
DROP TABLE IF EXISTS `song_singer`;
CREATE TABLE `song_singer`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键（自增）',
  `song_id` bigint NULL DEFAULT NULL COMMENT '歌曲ID',
  `singer_id` bigint NULL DEFAULT NULL COMMENT '歌手ID',
  `singer_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '歌手名称',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `song_id`(`song_id` ASC) USING BTREE,
  INDEX `singer_id`(`singer_id` ASC) USING BTREE,
  CONSTRAINT `singer_id` FOREIGN KEY (`singer_id`) REFERENCES `singers` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `song_id` FOREIGN KEY (`song_id`) REFERENCES `songs` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 105 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for songs
-- ----------------------------
DROP TABLE IF EXISTS `songs`;
CREATE TABLE `songs`  (
  `id` bigint NOT NULL COMMENT '歌曲ID',
  `name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '歌名',
  `duration` varchar(5) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '时长（格式：mm:ss）',
  `audio_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '歌曲地址',
  `lyric_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '歌词地址',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '歌曲封面地址',
  `album_id` bigint NULL DEFAULT NULL COMMENT '专辑ID',
  `album_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '专辑名称',
  `state` int NOT NULL DEFAULT 0 COMMENT '0-审核中，1-审核通过，2-审核不通过',
  `favorite_count` int NULL DEFAULT 0 COMMENT '歌曲点赞数量',
  `review_comment` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核不通过原因',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_song_name`(`name` ASC) USING BTREE COMMENT '歌曲名索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` bigint NOT NULL COMMENT '用户ID,主键',
  `username` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户昵称（唯一）',
  `email` varchar(25) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户邮箱',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用户密码（加密）',
  `gender` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '性别',
  `state` tinyint NOT NULL COMMENT '0-普通用户，1-后台管理员',
  `bio` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '个人简介',
  `avatar` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '头像（存储位置）',
  `account_status` int NOT NULL DEFAULT 0 COMMENT '0-正常，1-异常，2-锁定，3-待审核',
  `created_at` datetime NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `email`(`email` ASC) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
