package com.hope.music.media.domain.event;

/** A newly stored audio file, scheduled only after its song transaction commits. */
public record UploadedAudioEvent(String songId, String source) { }
