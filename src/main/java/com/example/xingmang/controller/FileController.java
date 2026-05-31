package com.example.xingmang.controller;

import com.example.xingmang.model.dto.FileCompleteUploadDTO;
import com.example.xingmang.model.dto.FileUploadRequestDTO;
import com.example.xingmang.model.vo.FileCheckVO;
import com.example.xingmang.model.vo.FileUploadVO;
import com.example.xingmang.model.vo.Result;
import com.example.xingmang.service.FileService;
import org.springframework.web.bind.annotation.*;
import com.example.xingmang.model.dto.MultipartUploadCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadInitDTO;
import com.example.xingmang.model.dto.MultipartUploadPartCompleteDTO;
import com.example.xingmang.model.dto.MultipartUploadUrlDTO;
import com.example.xingmang.model.vo.MultipartUploadCheckVO;
import com.example.xingmang.model.vo.MultipartUploadInitVO;
import com.example.xingmang.model.vo.MultipartUploadUrlVO;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 秒传检查
     */
    @GetMapping("/check")
    public Result<FileCheckVO> checkFile(@RequestParam("fileMd5") String fileMd5) {
        return Result.success(fileService.checkFile(fileMd5));
    }

    /**
     * 生成上传预签名 URL
     */
    @PostMapping("/upload-url")
    public Result<FileUploadVO> generateUploadUrl(@RequestBody FileUploadRequestDTO dto) {
        return Result.success(fileService.generateUploadUrl(dto));
    }

    /**
     * 上传完成后确认入库
     */
    @PostMapping("/complete")
    public Result<Long> completeUpload(@RequestBody FileCompleteUploadDTO dto) {
        return Result.success(fileService.completeUpload(dto));
    }

    /**
     * 获取文件访问地址
     */
    @GetMapping("/view-url/{fileId}")
    public Result<String> getViewUrl(@PathVariable Long fileId) {
        return Result.success(fileService.getViewUrl(fileId));
    }

    /**
     * 初始化分片上传任务
     */
    @PostMapping("/multipart/init")
    public Result<MultipartUploadInitVO> initMultipartUpload(@RequestBody MultipartUploadInitDTO dto) {
        return Result.success(fileService.initMultipartUpload(dto));
    }

    /**
     * 查询已上传分片
     */
    @GetMapping("/multipart/check")
    public Result<MultipartUploadCheckVO> checkMultipartUpload(@RequestParam("uploadId") String uploadId) {
        return Result.success(fileService.checkMultipartUpload(uploadId));
    }

    /**
     * 获取某一片的预签名URL
     */
    @PostMapping("/multipart/presigned-url")
    public Result<MultipartUploadUrlVO> getMultipartUploadUrl(@RequestBody MultipartUploadUrlDTO dto) {
        return Result.success(fileService.getMultipartUploadUrl(dto));
    }

    /**
     * 确认某一片上传完成
     */
    @PostMapping("/multipart/part-complete")
    public Result<Void> confirmMultipartPart(@RequestBody MultipartUploadPartCompleteDTO dto) {
        fileService.confirmMultipartPart(dto);
        return Result.success();
    }

    /**
     * 完成上传并合并分片
     */
    @PostMapping("/multipart/complete")
    public Result<Long> completeMultipartUpload(@RequestBody MultipartUploadCompleteDTO dto) {
        return Result.success(fileService.completeMultipartUpload(dto));
    }

}
