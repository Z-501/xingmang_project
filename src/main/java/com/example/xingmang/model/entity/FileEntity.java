package com.example.xingmang.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_file")
public class FileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 上传该文件的用户 ID
     */
    private Long userId;

    /**
     * MinIO 桶名
     */
    private String bucketName;

    /**
     * MinIO 对象名（真正存储路径）
     */
    private String objectName;

    /**
     * 原始文件名
     */
    private String originalFileName;

    /**
     * 文件类型，例如 image/png、video/mp4
     */
    private String fileType;

    /**
     * 文件大小，单位字节
     */
    private Long fileSize;

    /**
     * 文件 MD5，后续用于秒传
     */
    private String fileMd5;

    /**
     * 上传状态：0-待上传，1-已完成
     */
    private Integer uploadStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}