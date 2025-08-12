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
     * 需要被清理的所有临时分片的文件路径列表。
     */
    private final List<String> sourceFilePaths;


    /**
     * 构造一个文件合并事件对象。
     *
     * <p>此事件通常在多个文件分片合并完成后触发，
     * 包含合并后文件的元数据、所属批次ID，以及参与合并的源文件路径列表。
     *
     * @param source          事件的发布者，通常是触发该事件的组件或对象。
     * @param fileMetadata    合并完成后文件的元数据信息。
     * @param batchId         标识此次上传或合并操作的唯一批次ID。
     * @param sourceFilePaths 参与合并的源文件在存储中的路径列表。
     */
    public FileMergedEvent(Object source, FileMetadata fileMetadata, String batchId, List<String> sourceFilePaths) {
        super(source);
        this.fileMetadata = fileMetadata;
        this.batchId = batchId;
        this.sourceFilePaths = sourceFilePaths;
    }

} 