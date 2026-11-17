export interface Singer {
  id: string;
  name: string | null;
  avatar: string | null;
}
export interface Song {
  provider?: "local" | "qq";
  providerSongId?: string;
  mediaMid?: string;
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
export const songKey = (song: Song) => `${song.provider || "local"}:${song.songId}`;
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
