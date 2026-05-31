package com.example.xingmang.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.xingmang.model.entity.DanmuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DanmuMapper extends BaseMapper<DanmuEntity> {

    /**
     * 查询某个视频最近落库的弹幕（按 createTime 倒序）
     */
    @Select("""
            <script>
            select id, video_id, user_id, content, danmu_time, color, mode, font_size, status, create_time
            from t_danmu
            where video_id = #{videoId}
              and status = 1
            order by create_time desc, id desc
            limit #{limit}
            </script>
            """)
    List<DanmuEntity> selectLatestDanmus(@Param("videoId") Long videoId,
                                         @Param("limit") Integer limit);

    /**
     * 按视频时间轴区间查询历史弹幕
     */
    @Select("""
            <script>
            select id, video_id, user_id, content, danmu_time, color, mode, font_size, status, create_time
            from t_danmu
            where video_id = #{videoId}
              and status = 1
              and danmu_time &gt;= #{fromTime}
              and danmu_time &lt;= #{toTime}
            order by danmu_time asc, id asc
            limit #{limit}
            </script>
            """)
    List<DanmuEntity> selectHistoryDanmusByTimeRange(@Param("videoId") Long videoId,
                                                     @Param("fromTime") BigDecimal fromTime,
                                                     @Param("toTime") BigDecimal toTime,
                                                     @Param("limit") Integer limit);
}