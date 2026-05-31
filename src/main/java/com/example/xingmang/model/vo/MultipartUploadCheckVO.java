package com.example.xingmang.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MultipartUploadCheckVO {

    /**
     * 上传任务ID
     */
    private String uploadId;

    /**
     * 最终对象名
     */
    private String objectName;

    /**
     * 已上传完成的分片编号
     */
    private List<Integer> uploadedParts;
}
