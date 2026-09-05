package com.example.xingmang.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BenchmarkDanmuMapper {

    @Select("""
            SELECT COUNT(*)
            FROM t_danmu
            WHERE video_id = #{videoId}
              AND content LIKE CONCAT(#{prefix}, '%')
            """)
    long countPersisted(@Param("videoId") Long videoId, @Param("prefix") String prefix);

    @Delete("""
            DELETE FROM t_danmu
            WHERE video_id = #{videoId}
              AND content LIKE CONCAT(#{prefix}, '%')
            """)
    int deleteBenchmarkRows(@Param("videoId") Long videoId, @Param("prefix") String prefix);
}
