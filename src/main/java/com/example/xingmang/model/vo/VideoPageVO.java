package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VideoPageVO {

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;

    /**
     * 总条数
     */
    private Long total;

    /**
     * 是否还有下一页
     */
    private Boolean hasMore;

    /**
     * 当前页数据
     */
    private List<VideoCardVO> records;
}
