package service;

import annotation.ParamName;
import pojo.Singer;

public interface SingerService {

    boolean applyMusician(Singer singer,String avatarBase64,String idCardFrontBase64, String idCardBackBase64);

    int getStatus(String singerId);
}
