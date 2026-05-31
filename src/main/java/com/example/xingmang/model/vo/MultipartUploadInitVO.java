package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MultipartUploadInitVO {

    /**
     * 是否命中秒传
     */
    private Boolean existed;

    /**
     * 秒传命中时返回 fileId
     */
    private Long fileId;

    /**
     * 上传任务ID
     */
    private String uploadId;

    /**
     * 最终文件对象名
     */
    private String objectName;

    /**
     * 总分片数
     */
    private Integer totalChunks;
}
