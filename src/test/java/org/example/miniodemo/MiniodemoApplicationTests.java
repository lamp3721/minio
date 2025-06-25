package org.example.miniodemo;

import org.example.miniodemo.domain.FileMetadata;
import org.example.miniodemo.service.FileMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
class MiniodemoApplicationTests {

    @Autowired
    FileMetadataService fileMetadataService;

    @Test
    void contextLoads() {
        List<FileMetadata> list = fileMetadataService.list();
    }

}
