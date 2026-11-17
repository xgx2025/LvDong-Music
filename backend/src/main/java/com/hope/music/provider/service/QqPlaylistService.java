package com.hope.music.provider.service;

import com.hope.music.provider.dao.QqPlaylistDao;
import com.hope.music.provider.domain.vo.QqLibraryVO.*;
import com.hope.music.provider.service.QqMusicConnectionService.MusicSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** QQ library adapter and bounded, complete snapshot imports without audio duplication. */
@Service
public class QqPlaylistService {
    private static final int MAX_SONGS = 5000;
    private final QqMusicClient client;
    private final QqMusicConnectionService connections;
    private final QqPlaylistDao snapshots;
    public QqPlaylistService(QqMusicClient client, QqMusicConnectionService connections, QqPlaylistDao snapshots) {
        this.client = client; this.connections = connections; this.snapshots = snapshots;
    }

    public PlaylistPage playlists(String userId, int favoriteOffset) {
        return connections.withSession(userId, session -> remotePlaylists(session, favoriteOffset).page());
    }
    public List<Song> dailySongs(String userId) {
        return connections.withSession(userId, session -> {
            JsonNode songs = client.get("/user/daily-songs", Map.of(), session.token()).path("songs");
            if (!songs.isArray() || songs.size() > 100) throw invalid();
            var normalized = new LinkedHashMap<String, Song>();
            for (JsonNode raw : songs) {
                Song entry = song(raw);
                normalized.putIfAbsent(entry.providerSongId(), entry);
            }
            return List.copyOf(normalized.values());
        });
    }
    public SongPage remoteSongs(String userId, String id, int offset, int limit) {
        return connections.withSession(userId, session -> readSongs(session, findRemote(session, id), offset, limit));
    }
    public Lyrics lyrics(String userId, String songMid) {
        return connections.withSession(userId, session -> {
            JsonNode response = client.get("/getLyric", Map.of("songmid", songMid, "isFormat", "false"), session.token()).path("response");
            if (!response.path("code").isNumber() || response.path("code").asInt(-1) != 0) throw invalid();
            JsonNode lyric = response.path("lyric");
            if (!lyric.isMissingNode() && !lyric.isNull() && !lyric.isString()) throw invalid();
            String text = lyric.asString("");
            if (text.length() > 524288) throw invalid();
            return new Lyrics(org.springframework.web.util.HtmlUtils.htmlUnescape(text));
        });
    }
    public ImportedPlaylist importPlaylist(String userId, String id) {
        return connections.withSession(userId, session -> {
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(3).toNanos();
            RemotePlaylist playlist = findRemote(session, id);
            LinkedHashMap<String, Song> songs = new LinkedHashMap<>();
            int offset = 0;
            for (int page = 0; page <= MAX_SONGS / 100; page++) {
                if (System.nanoTime() > deadline) throw incomplete();
                SongPage result = readSongs(session, playlist, offset, 100);
                if (System.nanoTime() > deadline) throw incomplete();
                if (result.total() > MAX_SONGS) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "单个歌单最多导入5000首歌曲");
                int before = songs.size();
                for (Song song : result.items()) songs.putIfAbsent(song.providerSongId(), song);
                if (songs.size() > MAX_SONGS) throw incomplete();
                if (!result.more()) return snapshots.replace(userId, session.accountId(), playlist.value(), List.copyOf(songs.values()));
                if (result.items().isEmpty() || songs.size() == before || result.nextOffset() <= offset) throw incomplete();
                offset = result.nextOffset();
            }
            throw incomplete();
        });
    }
    public List<ImportedPlaylist> imported(String userId) { return snapshots.list(userId); }
    @org.springframework.transaction.annotation.Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SongPage importedSongs(String userId, String id, int offset, int limit) {
        ImportedPlaylist playlist = snapshots.find(userId, id);
        if (playlist == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入歌单不存在");
        List<Song> songs = snapshots.songs(userId, id, offset, limit);
        return new SongPage(songs, playlist.songCount(), offset, offset + songs.size(), offset + songs.size() < playlist.songCount());
    }
    public void deleteImported(String userId, String id) { snapshots.delete(userId, id); }

    public Playback playback(String userId, String songMid, String mediaMid) {
        return connections.withSession(userId, session -> {
            var query = new HashMap<String, String>();
            query.put("songmid", songMid); query.put("quality", "128");
            if (mediaMid != null && !mediaMid.isBlank()) query.put("mediaId", mediaMid);
            JsonNode entry = client.get("/getMusicPlay", query, session.token()).path("data").path("playUrl").path(songMid);
            String url = playbackUrl(text(entry, "url"));
            if (!url.isEmpty()) return new Playback(url, entry.path("preview").asBoolean(false), "standard");
            query.put("preview", "1");
            entry = client.get("/getMusicPlay", query, session.token()).path("data").path("playUrl").path(songMid);
            url = playbackUrl(text(entry, "url"));
            if (!url.isEmpty()) return new Playback(url, entry.path("preview").asBoolean(false), "standard");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "QQ 音乐暂未提供这首歌的播放地址，可能受账号权限或地区限制");
        });
    }

    private RemotePage remotePlaylists(MusicSession session, int offset) {
        JsonNode response = client.get("/user/playlist", offset == 0 ? Map.of() : Map.of("favoriteOffset", String.valueOf(offset)), session.token());
        if (!response.path("playlist").isArray()) throw invalid();
        var entries = new LinkedHashMap<String, RemotePlaylist>();
        if (offset == 0) entries.put("liked", new RemotePlaylist(new Playlist("liked", "我喜欢", null, null, true, true), "201"));
        for (JsonNode raw : response.path("playlist")) {
            boolean owned = raw.has("dirName");
            String dir = first(raw, "dirId", "dirid");
            boolean liked = owned && dir.equals("201");
            String id = liked ? "liked" : first(raw, "tid", "dissid", "id");
            if (!id.matches("[0-9]{1,30}") && !liked) continue;
            String name = liked ? "我喜欢" : first(raw, "dirName", "dirname", "name", "dissname", "title");
            if (name.isBlank()) name = "未命名歌单";
            if (name.length() > 200) name = name.substring(0, 200);
            String cover = safeImage(first(raw, "bigpicUrl", "picUrl", "picurl", "coverUrl", "logo"));
            JsonNode count = raw.has("songNum") ? raw.path("songNum") : raw.path("songnum");
            Integer trackCount = count.isNumber() && count.asInt(-1) >= 0 ? count.asInt() : null;
            entries.putIfAbsent(id, new RemotePlaylist(new Playlist(id, name, cover, trackCount, owned, liked), dir));
            if (liked) entries.put(id, new RemotePlaylist(new Playlist(id, name, cover, trackCount, true, true), dir));
        }
        boolean more = response.path("more").asBoolean(false);
        JsonNode next = response.path("nextFavoriteOffset");
        Integer nextOffset = more && next.isNumber() && next.asInt(-1) > offset ? next.asInt() : null;
        var values = List.copyOf(entries.values());
        int total = Math.max(values.size(), response.path("total").asInt(values.size()));
        return new RemotePage(new PlaylistPage(values.stream().map(RemotePlaylist::value).toList(), total, more, nextOffset), values);
    }
    private RemotePlaylist findRemote(MusicSession session, String id) {
        if (id.equals("liked")) return new RemotePlaylist(new Playlist("liked", "我喜欢", null, null, true, true), "201");
        int offset = 0;
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(2).toNanos();
        for (int round = 0; round < 20; round++) {
            if (System.nanoTime() > deadline) throw incomplete();
            RemotePage response = remotePlaylists(session, offset);
            for (RemotePlaylist entry : response.entries()) if (entry.value().id().equals(id)) return entry;
            if (!response.page().more()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "当前 QQ 账号没有这个歌单");
            if (response.page().nextFavoriteOffset() == null) throw incomplete();
            offset = response.page().nextFavoriteOffset();
        }
        throw incomplete();
    }
    private SongPage readSongs(MusicSession session, RemotePlaylist playlist, int offset, int limit) {
        JsonNode songs;
        int total;
        boolean more;
        if (playlist.value().owned()) {
            String path = playlist.value().liked() ? "/user/liked-songs" : "/user/playlist-detail";
            var query = new HashMap<String, String>();
            query.put("offset", String.valueOf(offset)); query.put("limit", String.valueOf(limit));
            if (!playlist.value().liked()) {
                query.put("tid", playlist.value().id());
                if (playlist.dirId().matches("[0-9]+")) query.put("dirid", playlist.dirId());
            }
            JsonNode response = client.get(path, query, session.token());
            songs = response.path("songs");
            if (!songs.isArray() || songs.size() > limit) throw invalid();
            total = Math.max(0, response.path("total").asInt(offset + songs.size()));
            more = response.path("more").asBoolean(false) || offset + songs.size() < total;
        } else {
            JsonNode response = client.get("/getSongListDetail", Map.of("disstid", playlist.value().id()), session.token()).path("response");
            JsonNode detail = response.path("cdlist").path(0);
            if (response.path("code").asInt(-1) != 0 || text(detail, "dissname").isBlank() || !detail.path("songlist").isArray()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "这个 QQ 歌单暂时无法读取，可能未公开或已失效");
            }
            JsonNode all = detail.path("songlist");
            total = Math.max(all.size(), detail.path("total_song_num").asInt(all.size()));
            var normalized = new ArrayList<Song>();
            for (int i = offset; i < Math.min(all.size(), offset + limit); i++) normalized.add(song(all.get(i)));
            more = offset + normalized.size() < total || detail.path("hasmore").asInt(0) == 1;
            if (offset + normalized.size() < all.size()) more = true;
            // hasmore describes an incomplete upstream list, not an endless next page after the last song.
            if (detail.path("hasmore").asInt(0) == 1 && offset + normalized.size() >= all.size()) throw incomplete();
            return new SongPage(List.copyOf(normalized), total, offset, offset + normalized.size(), more);
        }
        List<Song> normalized = new ArrayList<>();
        for (JsonNode raw : songs) normalized.add(song(raw));
        return new SongPage(List.copyOf(normalized), total, offset, offset + songs.size(), more);
    }

    private static Song song(JsonNode raw) {
        String mid = first(raw, "mid", "songmid");
        if (!mid.matches("[A-Za-z0-9]{1,100}")) throw invalid();
        String media = first(raw.path("file"), "media_mid", "mediaMid");
        if (media.isEmpty()) media = first(raw, "mediaMid", "media_mid");
        if (!media.matches("[A-Za-z0-9]{0,100}")) media = "";
        String title = first(raw, "title", "name", "songname");
        String albumMid = first(raw.path("album"), "mid", "albummid");
        if (albumMid.isEmpty()) albumMid = first(raw, "albummid");
        String albumName = first(raw.path("album"), "title", "name");
        if (albumName.isEmpty()) albumName = first(raw, "albumname");
        int seconds = Math.max(0, raw.path("interval").asInt(0));
        String duration = "%d:%02d".formatted(seconds / 60, seconds % 60);
        var singers = new ArrayList<Singer>();
        JsonNode source = raw.path("singer").isArray() ? raw.path("singer") : raw.path("singers");
        for (JsonNode singer : source) {
            String name = first(singer, "name", "title", "singername");
            if (!name.isBlank()) singers.add(new Singer(first(singer, "mid", "id", "singermid"), name, null));
        }
        String cover = albumMid.matches("[A-Za-z0-9]{1,100}") ? "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg" : null;
        return new Song("qq:" + mid, "qq", mid, media, title.isBlank() ? "未知歌曲" : title, duration,
                albumMid, albumName, cover, null, 0, false, List.copyOf(singers));
    }
    private static String first(JsonNode raw, String... fields) {
        for (String field : fields) { String value = text(raw, field); if (!value.isBlank()) return value; }
        return "";
    }
    private static String text(JsonNode raw, String field) {
        JsonNode value = raw.path(field);
        return value.isString() || value.isNumber() ? value.asString("") : "";
    }
    private static String safeImage(String raw) {
        if (raw.isEmpty() || raw.length() > 1000) return null;
        try {
            URI uri = URI.create(raw.startsWith("//") ? "https:" + raw : raw);
            return uri.getHost() != null && uri.getUserInfo() == null && List.of("https", "http").contains(uri.getScheme()) ? uri.toString() : null;
        } catch (IllegalArgumentException e) { return null; }
    }
    private static String playbackUrl(String raw) {
        if (raw.isEmpty()) return "";
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null || !List.of("https", "http").contains(uri.getScheme())
                    || !(host.endsWith(".qq.com") || host.endsWith(".qqmusic.com")) || (uri.getPort() != -1 && uri.getPort() != 443)) throw invalid();
            return uri.getScheme().equals("http") ? "https" + raw.substring(4) : raw;
        } catch (IllegalArgumentException e) { throw invalid(); }
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 音乐返回的数据暂时无法读取，请稍后重试"); }
    private static ResponseStatusException incomplete() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "QQ 歌单未完整加载，未覆盖已有导入，请稍后重试"); }
    private record RemotePlaylist(Playlist value, String dirId) { }
    private record RemotePage(PlaylistPage page, List<RemotePlaylist> entries) { }
}
