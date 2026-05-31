package com.example.xingmang.service;

import com.example.xingmang.model.dto.FileCompleteUploadDTO;
import com.example.xingmang.model.dto.FileUploadRequestDTO;
import com.example.xingmang.model.vo.FileCheckVO;
import com.example.xingmang.model.vo.FileUploadVO;
import com.example.xingmang.model.dto.MultipartUploadCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadInitDTO;
import com.example.xingmang.model.dto.MultipartUploadPartCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadUrlDTO;
import com.example.xingmang.model.vo.MultipartUploadCheckVO;
import com.example.xingmang.model.vo.MultipartUploadInitVO;
import com.example.xingmang.model.vo.MultipartUploadUrlVO;
import java.util.Collection;
import java.util.Map;
import java.io.InputStream;

public interface FileService {

    /**
     * 检查文件是否已存在（秒传基础）
     */
    FileCheckVO checkFile(String fileMd5);

    /**
     * 生成上传预签名 URL
     */
    FileUploadVO generateUploadUrl(FileUploadRequestDTO dto);

    /**
     * 上传完成后保存文件记录
     */
    Long completeUpload(FileCompleteUploadDTO dto);

    /**
     * 根据 fileId 生成预览地址
     */
    String getViewUrl(Long fileId);

    Map<Long, String> getViewUrlMap(Collection<Long> fileIds);

    /**
     * 初始化分片上传任务
     */
    MultipartUploadInitVO initMultipartUpload(MultipartUploadInitDTO dto);

    /**
     * 查询分片上传进度
     */
    MultipartUploadCheckVO checkMultipartUpload(String uploadId);

    /**
     * 获取某一片的预签名上传URL
     */
    MultipartUploadUrlVO getMultipartUploadUrl(MultipartUploadUrlDTO dto);

    /**
     * 确认某一片上传完成
     */
    void confirmMultipartPart(MultipartUploadPartCompleteDTO dto);

    /**
     * 完成分片上传并合并文件
     */
    Long completeMultipartUpload(MultipartUploadCompleteDTO dto);

    /**
     * 打开某个文件的对象流，供服务端内部处理使用
     */
    InputStream openFileStream(Long fileId);

    /**
     * 保存后端生成的文件（例如遮罩图），写入 MinIO 并登记到 t_file，返回 fileId
     */
    Long saveGeneratedFile(byte[] bytes,
                           String objectNamePrefix,
                           String originalFileName,
                           String contentType,
                           Long userId);

}