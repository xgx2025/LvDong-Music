package dao;

import pojo.Singer;

import java.util.List;

public interface SingerDao {

    List<Singer> searchSingersByKeyword(String keyword, int page, int pageSize);

    int countSingersByKeyword(String keyword);

    void addSinger(Singer singer);

    int getStatus(Long singerId);

    String findSingerNameById(Long id);
}
