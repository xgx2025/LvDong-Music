package pojo;


import java.util.List;

public class SearchResult {
    private int songCount;
    private int albumCount;
    private int singerCount;
    private int playlistCount;
    private List<Song> songs;
    private List<Album> albums;
    private List<Singer> singers;
    private List<Playlist> playlists;

    public SearchResult() {
    }

    public SearchResult(int songCount, int albumCount, int singerCount, int playlistCount, List<Song> songs, List<Album> albums, List<Singer> singers, List<Playlist> playlists) {
        this.songCount = songCount;
        this.albumCount = albumCount;
        this.singerCount = singerCount;
        this.playlistCount = playlistCount;
        this.songs = songs;
        this.albums = albums;
        this.singers = singers;
        this.playlists = playlists;
    }

    public int getSongCount() {
        return songCount;
    }

    public void setSongCount(int songCount) {
        this.songCount = songCount;
    }

    public int getAlbumCount() {
        return albumCount;
    }

    public void setAlbumCount(int albumCount) {
        this.albumCount = albumCount;
    }

    public int getSingerCount() {
        return singerCount;
    }

    public void setSingerCount(int singerCount) {
        this.singerCount = singerCount;
    }

    public int getPlaylistCount() {
        return playlistCount;
    }

    public void setPlaylistCount(int playlistCount) {
        this.playlistCount = playlistCount;
    }

    public List<Song> getSongs() {
        return songs;
    }

    public void setSongs(List<Song> songs) {
        this.songs = songs;
    }

    public List<Album> getAlbums() {
        return albums;
    }

    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    public List<Singer> getSingers() {
        return singers;
    }

    public void setSingers(List<Singer> singers) {
        this.singers = singers;
    }

    public List<Playlist> getPlaylists() {
        return playlists;
    }

    public void setPlaylists(List<Playlist> playlists) {
        this.playlists = playlists;
    }
}