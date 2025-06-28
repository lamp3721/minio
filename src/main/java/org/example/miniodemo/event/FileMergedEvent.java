package org.example.miniodemo.event;

import lombok.Getter;
import org.example.miniodemo.domain.FileMetadata;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 文件合并成功后发布的事件。
 * <p>
 * 此事件携带了成功合并后的文件元数据以及清理临时分片所需的信息。
 * 它用于解耦文件合并操作与后续的数据持久化和清理任务。
 */
@Getter
public class FileMergedEvent extends ApplicationEvent {

    /**
     * 已合并文件的元数据对象（此时尚未持久化）。
     */
    private final FileMetadata fileMetadata;

    /**
     * 本次合并所属的批次ID。
     */
    private final String batchId;

    /**
     * 需要被清理的所有临时分片的对象名称列表。
     */
    private final List<String> sourceObjectNames;


    public FileMergedEvent(Object source, FileMetadata fileMetadata, String batchId, List<String> sourceObjectNames) {
        super(source);
        this.fileMetadata = fileMetadata;
        this.batchId = batchId;
        this.sourceObjectNames = sourceObjectNames;
    }
} 