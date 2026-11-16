export interface Singer {
  id: string;
  name: string | null;
  avatar: string | null;
}
export interface Song {
  songId: string;
  songName: string | null;
  duration: string | null;
  albumId: string | null;
  albumName: string | null;
  coverUrl: string | null;
  lyricUrl: string | null;
  favoriteCount: number;
  userHasFavorite: boolean;
  singers: Singer[];
}
export interface SearchResult {
  songs: Song[];
  singers: Singer[];
  songCount: number;
  singerCount: number;
  albums: unknown[];
  playlists: unknown[];
  albumCount: number;
  playlistCount: number;
}
