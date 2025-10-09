package org.example.miniodemo.service;

import org.example.miniodemo.dto.FileDetailDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

public interface PrivateFileService extends AbstractChunkedFile {

    //列出私有存储桶中所有最终合并完成的文件。
    List<FileDetailDto> listPrivateFiles();

    // 获取私有文件的预签名下载URL（推荐的下载方式）。
    String getPresignedPrivateDownloadUrl(String objectName) ;

    // 获取用于代理下载的私有文件输入流。
    InputStream downloadPrivateFile(String filePath);
}
