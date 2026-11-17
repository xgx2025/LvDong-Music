package com.hope.music.recognition.service;

/** External lyric inference boundary, replaceable by an isolated test fixture. */
public interface RecognitionProvider {
    record SongHint(String song, String singer) { }
    SongHint recognize(String text);
}
