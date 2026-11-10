package pojo;

import java.util.List;

public class Song {
    private int rank;
    private String songId;
    private String songName;
    private List<Singer> singers;
    private String albumName;
    private String albumId;
    private String audioUrl;
    private String coverUrl;
    private String lyricUrl;
    private String duration;
    private int favoriteCount;
    private boolean userHasFavorite;


    public Song() {
    }

    public Song(int rank, String songId, String songName, List<Singer> singers, String albumName, String albumId, String coverUrl, String lyricUrl, String duration) {
        this.rank = rank;
        this.songId = songId;
        this.songName = songName;
        this.singers = singers;
        this.albumName = albumName;
        this.albumId = albumId;
        this.coverUrl = coverUrl;
        this.lyricUrl = lyricUrl;
        this.duration = duration;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public String getSongName() {
        return songName;
    }

    public void setSongName(String songName) {
        this.songName = songName;
    }

    public List<Singer> getSingers() {
        return singers;
    }

    public void setSingers(List<Singer> singers) {
        this.singers = singers;
    }

    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getLyricUrl() {
        return lyricUrl;
    }

    public void setLyricUrl(String lyricUrl) {
        this.lyricUrl = lyricUrl;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public int getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(int favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public boolean isUserHasFavorite() {
        return userHasFavorite;
    }

    public void setUserHasFavorite(boolean userHasFavorite) {
        this.userHasFavorite = userHasFavorite;
    }

    @Override
    public String toString() {
        return "Song{" +
                "rank=" + rank +
                ", songId='" + songId + '\'' +
                ", songName='" + songName + '\'' +
                ", singers=" + singers +
                ", albumName='" + albumName + '\'' +
                ", albumId='" + albumId + '\'' +
                ", audioUrl='" + audioUrl + '\'' +
                ", coverUrl='" + coverUrl + '\'' +
                ", lyricUrl='" + lyricUrl + '\'' +
                ", duration='" + duration + '\'' +
                ", favoriteCount=" + favoriteCount +
                ", userHasFavorite=" + userHasFavorite +
                '}';
    }
}
