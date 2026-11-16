package com.hope.music.search.domain.vo;

import java.util.List;

/** Public search projections deliberately exclude audio paths and private user data. */
public record SearchVO(long songCount, long albumCount, long singerCount, long playlistCount,
                       List<SongVO> songs, List<Object> albums, List<SingerVO> singers,
                       List<Object> playlists) {
    public record SingerVO(String id, String name, String avatar) { }
    public record SongVO(int rank, String songId, String songName, String duration,
                         String albumId, String albumName, String coverUrl, String lyricUrl,
                         int favoriteCount, boolean userHasFavorite, List<SingerVO> singers) { }
}
