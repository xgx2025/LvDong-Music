package service;

import java.util.Map;

public interface RecognitionService {
    Map<String, Object> musicRecognition(String text);
}
