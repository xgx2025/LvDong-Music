package com.hope.music.provider.controller;

import com.hope.music.auth.service.SessionService;
import com.hope.music.common.Result;
import com.hope.music.provider.domain.vo.QqLibraryVO.*;
import com.hope.music.provider.service.QqPlaylistService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** QQ remote libraries and imported snapshots scoped to the authenticated website user. */
@RestController
@RequestMapping("/api/v1/music-providers/qq")
public class QqPlaylistController {
    private final SessionService sessions;
    private final QqPlaylistService playlists;
    public QqPlaylistController(SessionService sessions, QqPlaylistService playlists) { this.sessions = sessions; this.playlists = playlists; }
    @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/playlists")
    public Result<PlaylistPage> remote(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @RequestParam(defaultValue = "0") @Min(0) @Max(20000) int favoriteOffset) {
        return Result.success(playlists.playlists(sessions.identity(auth), favoriteOffset));
    }
    @GetMapping("/playlists/{id}/songs")
    public Result<SongPage> remoteSongs(@RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable @Pattern(regexp = "liked|[0-9]{1,30}") String id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return Result.success(playlists.remoteSongs(sessions.identity(auth), id, offset, limit));
    }
    @PostMapping("/imports")
    public Result<ImportedPlaylist> importPlaylist(@RequestHeader(value = "Authorization", required = false) String auth,
                                                  @Valid @RequestBody ImportRequest request) {
        return Result.success(playlists.importPlaylist(sessions.identity(auth), request.playlistId()));
    }
    @GetMapping("/imports")
    public Result<List<ImportedPlaylist>> imported(@RequestHeader(value = "Authorization", required = false) String auth) {
        return Result.success(playlists.imported(sessions.identity(auth)));
    }
    @GetMapping("/imports/{id}/songs")
    public Result<SongPage> importedSongs(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return Result.success(playlists.importedSongs(sessions.identity(auth), id, offset, limit));
    }
    @DeleteMapping("/imports/{id}")
    public Result<Void> delete(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        playlists.deleteImported(sessions.identity(auth), id); return Result.success(null);
    }
    @PostMapping("/playback")
    public Result<Playback> playback(@RequestHeader(value = "Authorization", required = false) String auth, @Valid @RequestBody PlaybackRequest request) {
        return Result.success(playlists.playback(sessions.identity(auth), request.songMid(), request.mediaMid()));
    }
    /** Import accepts an ID, never client-supplied song data or playlist ownership flags. */
    public record ImportRequest(@NotNull @Pattern(regexp = "liked|[0-9]{1,30}") String playlistId) { }
    /** Stable provider identifiers only, never arbitrary media URLs. */
    public record PlaybackRequest(@NotNull @Pattern(regexp = "[A-Za-z0-9]{1,100}") String songMid,
                                  @Pattern(regexp = "[A-Za-z0-9]{0,100}") String mediaMid) { }
}
