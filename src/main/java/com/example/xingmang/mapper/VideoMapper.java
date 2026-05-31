package com.example.xingmang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.xingmang.model.entity.VideoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface VideoMapper extends BaseMapper<VideoEntity> {

    /**
     * 根据作者ID列表查询其已发布视频
     */
    @Select("""
            <script>
            select id, user_id, file_id, cover_file_id, title, description, duration, status, publish_time, create_time, update_time
            from t_video
            where status = 1
              and user_id in
              <foreach collection="authorIds" item="authorId" open="(" separator="," close=")">
                  #{authorId}
              </foreach>
            order by publish_time desc, id desc
            limit #{limit}
            </script>
            """)
    List<VideoEntity> selectPublishedVideosByAuthorIds(@Param("authorIds") Collection<Long> authorIds,
                                                       @Param("limit") Integer limit);
}
