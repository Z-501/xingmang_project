package com.example.xingmang.model.dto;
import lombok.Data;

/**
 *  前端请求“生成上传 URL”时传参数。
 */
@Data
public class FileUploadRequestDTO {

    /**
     * 原始文件名，例如 test.mp4
     */
    private String fileName;

    /**
     * 文件类型，例如 video/mp4
     */
    private String contentType;

    /**
     * 文件大小，单位字节
     */
    private Long fileSize;

    /**
     * 文件 md5，用于秒传
     */
    private String fileMd5;
}
