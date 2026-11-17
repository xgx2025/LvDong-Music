package com.hope.music;

import com.hope.music.media.domain.event.UploadedAudioEvent;
import com.hope.music.media.service.MediaPreprocessingService;
import com.hope.music.media.service.MediaService;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/** A failed or saturated converter must not turn a committed upload into an HTTP failure. */
class MediaPreprocessingServiceTest {
    @Test void schedulingFailureDoesNotFailTheCommittedUpload() throws Exception {
        var media=mock(MediaService.class);
        when(media.preprocessUploaded("1","source")).thenThrow(new IOException("unavailable"));
        var listener=new MediaPreprocessingService(media);
        assertThatCode(()->listener.preprocessUploadedAudio(new UploadedAudioEvent("1","source"))).doesNotThrowAnyException();
        verify(media).preprocessUploaded("1","source");
    }
    @Test void backgroundFailureDoesNotFailTheCommittedUpload() throws Exception {
        var media=mock(MediaService.class);
        when(media.preprocessUploaded("1","source")).thenReturn(CompletableFuture.failedFuture(new IOException("conversion failed")));
        var listener=new MediaPreprocessingService(media);
        assertThatCode(()->listener.preprocessUploadedAudio(new UploadedAudioEvent("1","source"))).doesNotThrowAnyException();
    }
}
