package org.example.miniodemo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.miniodemo.domain.ChunkUploadSession;
import org.example.miniodemo.domain.ChunkUploadStatus;
import org.example.miniodemo.domain.StorageType;
import org.example.miniodemo.mapper.ChunkUploadSessionMapper;
import org.example.miniodemo.service.ChunkUploadSessionService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 分片上传会话服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadSessionServiceImpl implements ChunkUploadSessionService {
    
    private final ChunkUploadSessionMapper sessionMapper;
    private final ObjectMapper objectMapper;
    
    @Override
    public ChunkUploadSession createOrGetSession(String sessionId, String fileName, String fileHash, 
                                               Long fileSize, String contentType, String folderPath, 
                                               Integer totalChunks, String bucketName, StorageType storageType) {
        
        // 先尝试获取已存在的会话
        Optional<ChunkUploadSession> existingSession = getSession(sessionId);
        if (existingSession.isPresent()) {
            ChunkUploadSession session = existingSession.get();
            // 验证会话是否有效
            if (session.getExpiresAt().isAfter(LocalDateTime.now()) && 
                session.getStatus() != ChunkUploadStatus.EXPIRED) {
                log.info("【会话管理】找到有效的上传会话: {}", sessionId);
                return session;
            } else {
                // 会话已过期，删除旧会话
                log.info("【会话管理】删除过期会话: {}", sessionId);
                deleteSession(sessionId);
            }
        }
        
        // 创建新会话
        ChunkUploadSession session = new ChunkUploadSession();
        session.setSessionId(sessionId);
        session.setFileName(fileName);
        session.setFileHash(fileHash);
        session.setFileSize(fileSize);
        session.setContentType(contentType);
        session.setFolderPath(folderPath);
        session.setTotalChunks(totalChunks);
        session.setUploadedChunks(0);
        session.setChunkPathsJson("[]"); // 初始化为空数组
        session.setBucketName(bucketName);
        session.setStorageType(storageType);
        session.setStatus(ChunkUploadStatus.UPLOADING);
        session.setExpiresAt(LocalDateTime.now().plusHours(24)); // 24小时后过期
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        
        sessionMapper.insert(session);
        log.info("【会话管理】创建新的上传会话: {}", sessionId);
        return session;
    }
    
    @Override
    public synchronized void recordChunkUploaded(String sessionId, Integer chunkNumber, String chunkPath) {
        Optional<ChunkUploadSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            log.error("【会话管理】会话不存在: {}", sessionId);
            return;
        }
        
        ChunkUploadSession session = sessionOpt.get();
        
        // 解析已上传的分片路径
        List<String> chunkPaths = parseChunkPaths(session.getChunkPathsJson());
        
        // 确保列表足够大
        while (chunkPaths.size() < chunkNumber) {
            chunkPaths.add(null);
        }
        
        // 检查分片是否已经上传过
        if (chunkPaths.get(chunkNumber - 1) != null && !chunkPaths.get(chunkNumber - 1).isEmpty()) {
            log.warn("【会话管理】分片已存在，跳过: 会话={}, 分片={}", sessionId, chunkNumber);
            return;
        }
        
        // 记录分片路径（分片编号从1开始，数组索引从0开始）
        chunkPaths.set(chunkNumber - 1, chunkPath);
        
        // 计算已上传分片数
        int uploadedCount = (int) chunkPaths.stream().filter(path -> path != null && !path.isEmpty()).count();
        
        // 更新会话
        LambdaUpdateWrapper<ChunkUploadSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChunkUploadSession::getSessionId, sessionId)
                    .set(ChunkUploadSession::getChunkPathsJson, serializeChunkPaths(chunkPaths))
                    .set(ChunkUploadSession::getUploadedChunks, uploadedCount)
                    .set(ChunkUploadSession::getUpdatedAt, LocalDateTime.now());
        
        // 如果所有分片都已上传，更新状态为准备合并
        if (uploadedCount == session.getTotalChunks()) {
            updateWrapper.set(ChunkUploadSession::getStatus, ChunkUploadStatus.READY_TO_MERGE);
            log.info("【会话管理】所有分片已上传，更新状态为READY_TO_MERGE: 会话={}", sessionId);
        }
        
        sessionMapper.update(null, updateWrapper);
        log.info("【会话管理】记录分片上传: 会话={}, 分片={}, 已上传={}/{}, 状态={}", 
                sessionId, chunkNumber, uploadedCount, session.getTotalChunks(),
                uploadedCount == session.getTotalChunks() ? "READY_TO_MERGE" : "UPLOADING");
    }
    
    @Override
    public Optional<ChunkUploadSession> getSession(String sessionId) {
        LambdaQueryWrapper<ChunkUploadSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChunkUploadSession::getSessionId, sessionId);
        ChunkUploadSession session = sessionMapper.selectOne(queryWrapper);
        return Optional.ofNullable(session);
    }
    
    @Override
    public List<String> getUploadedChunkPaths(String sessionId) {
        Optional<ChunkUploadSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("【会话管理】获取分片路径时会话不存在: {}", sessionId);
            return new ArrayList<>();
        }
        
        ChunkUploadSession session = sessionOpt.get();
        List<String> chunkPaths = parseChunkPaths(session.getChunkPathsJson());
        
        // 确保返回按顺序排列的所有有效分片路径
        List<String> validPaths = new ArrayList<>();
        for (int i = 0; i < session.getTotalChunks(); i++) {
            if (i < chunkPaths.size() && chunkPaths.get(i) != null && !chunkPaths.get(i).isEmpty()) {
                validPaths.add(chunkPaths.get(i));
            } else {
                log.warn("【会话管理】分片 {} 缺失: 会话={}", i + 1, sessionId);
                // 如果有分片缺失，返回空列表表示不完整
                return new ArrayList<>();
            }
        }
        
        log.info("【会话管理】获取分片路径: 会话={}, 分片数={}", sessionId, validPaths.size());
        return validPaths;
    }
    
    @Override
    public boolean isReadyToMerge(String sessionId) {
        Optional<ChunkUploadSession> sessionOpt = getSession(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("【会话管理】检查合并状态时会话不存在: {}", sessionId);
            return false;
        }
        
        ChunkUploadSession session = sessionOpt.get();
        
        // 检查会话是否已过期
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("【会话管理】会话已过期: {}", sessionId);
            return false;
        }
        
        // 检查会话状态是否允许合并
        if (session.getStatus() == ChunkUploadStatus.COMPLETED || 
            session.getStatus() == ChunkUploadStatus.EXPIRED ||
            session.getStatus() == ChunkUploadStatus.FAILED) {
            log.warn("【会话管理】会话状态不允许合并: 会话={}, 状态={}", sessionId, session.getStatus());
            return false;
        }
        
        // 检查所有分片是否都已上传
        boolean allChunksUploaded = session.getUploadedChunks().equals(session.getTotalChunks());
        
        // 如果所有分片都已上传但状态还不是READY_TO_MERGE，则更新状态
        if (allChunksUploaded && session.getStatus() != ChunkUploadStatus.READY_TO_MERGE) {
            log.info("【会话管理】所有分片已上传，更新会话状态为READY_TO_MERGE: {}", sessionId);
            updateSessionStatus(sessionId, ChunkUploadStatus.READY_TO_MERGE);
        }
        
        log.info("【会话管理】检查合并状态: 会话={}, 已上传={}/{}, 状态={}, 可合并={}", 
                sessionId, session.getUploadedChunks(), session.getTotalChunks(), 
                session.getStatus(), allChunksUploaded);
        
        return allChunksUploaded;
    }
    
    @Override
    public void updateSessionStatus(String sessionId, ChunkUploadStatus status) {
        LambdaUpdateWrapper<ChunkUploadSession> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ChunkUploadSession::getSessionId, sessionId)
                    .set(ChunkUploadSession::getStatus, status)
                    .set(ChunkUploadSession::getUpdatedAt, LocalDateTime.now());
        
        sessionMapper.update(null, updateWrapper);
        log.info("【会话管理】更新会话状态: 会话={}, 状态={}", sessionId, status);
    }
    
    @Override
    public void deleteSession(String sessionId) {
        LambdaQueryWrapper<ChunkUploadSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChunkUploadSession::getSessionId, sessionId);
        sessionMapper.delete(queryWrapper);
        log.info("【会话管理】删除会话: {}", sessionId);
    }
    
    @Override
    public void cleanupExpiredSessions() {
        LambdaQueryWrapper<ChunkUploadSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(ChunkUploadSession::getExpiresAt, LocalDateTime.now())
                   .or()
                   .eq(ChunkUploadSession::getStatus, ChunkUploadStatus.EXPIRED);
        
        List<ChunkUploadSession> expiredSessions = sessionMapper.selectList(queryWrapper);
        if (!expiredSessions.isEmpty()) {
            sessionMapper.delete(queryWrapper);
            log.info("【会话管理】清理过期会话: {} 个", expiredSessions.size());
        }
    }
    
    /**
     * 解析分片路径JSON字符串
     */
    private List<String> parseChunkPaths(String chunkPathsJson) {
        try {
            if (chunkPathsJson == null || chunkPathsJson.trim().isEmpty()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(chunkPathsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("【会话管理】解析分片路径JSON失败: {}", chunkPathsJson, e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 序列化分片路径为JSON字符串
     */
    private String serializeChunkPaths(List<String> chunkPaths) {
        try {
            return objectMapper.writeValueAsString(chunkPaths);
        } catch (JsonProcessingException e) {
            log.error("【会话管理】序列化分片路径失败", e);
            return "[]";
        }
    }
}