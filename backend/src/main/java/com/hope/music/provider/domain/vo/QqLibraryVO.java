package com.hope.music.provider.domain.vo;

import java.util.List;

/** Public QQ library data, independent of upstream credential and response shapes. */
public final class QqLibraryVO {
    private QqLibraryVO() { }
    public record Playlist(String id, String name, String coverUrl, Integer trackCount, boolean owned, boolean liked) { }
    public record PlaylistPage(List<Playlist> items, int total, boolean more, Integer nextFavoriteOffset) { }
    public record Singer(String id, String name, String avatar) { }
    public record Song(String songId, String provider, String providerSongId, String mediaMid, String songName,
                       String duration, String albumId, String albumName, String coverUrl, String lyricUrl,
                       int favoriteCount, boolean userHasFavorite, List<Singer> singers) { }
    public record SongPage(List<Song> items, int total, int offset, int nextOffset, boolean more) { }
    public record ImportedPlaylist(String id, String providerPlaylistId, String name, String coverUrl, int songCount, long importedAt) { }
    public record Playback(String url, boolean preview, String quality) { }
}
