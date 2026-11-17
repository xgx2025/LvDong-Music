package com.hope.music.media.service;

import com.hope.music.media.domain.event.UploadedAudioEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Enqueues committed uploads without changing publication state or failing an already committed upload. */
@Service
public class MediaPreprocessingService {
    private static final Logger LOG = LoggerFactory.getLogger(MediaPreprocessingService.class);
    private final MediaService media;

    public MediaPreprocessingService(MediaService media) {
        this.media = media;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void preprocessUploadedAudio(UploadedAudioEvent event) {
        try {
            media.preprocessUploaded(event.songId(), event.source()).exceptionally(error -> {
                LOG.warn("Audio preprocessing failed for song {}; playback can retry", event.songId());
                return null;
            });
        } catch (Exception error) {
            LOG.warn("Audio preprocessing could not be queued for song {}; playback can retry", event.songId());
        }
    }
}
