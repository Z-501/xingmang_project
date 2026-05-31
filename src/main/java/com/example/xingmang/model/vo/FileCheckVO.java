package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 *  单独做秒传检查也可以复用。
 */
@Data
@Builder
public class FileCheckVO {

    /**
     * 文件是否已存在
     */
    private Boolean existed;

    /**
     * 已存在时返回 fileId
     */
    private Long fileId;

    /**
     * 已存在时返回对象名
     */
    private String objectName;
}