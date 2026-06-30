package com.example.dockb.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.dockb.common.BizException;
import com.example.dockb.common.PageResult;
import com.example.dockb.common.ResultCode;
import com.example.dockb.config.AppProperties;
import com.example.dockb.config.CacheConfig;
import com.example.dockb.entity.Document;
import com.example.dockb.entity.DocumentChunk;
import com.example.dockb.mapper.DocumentChunkMapper;
import com.example.dockb.mapper.DocumentMapper;
import com.example.dockb.service.DocumentService;
import com.example.dockb.service.M3Service;
import com.example.dockb.util.SnippetUtil;
import com.example.dockb.util.TextChunker;
import com.example.dockb.util.TextExtractor;
import com.example.dockb.util.TrustedDocuments;
import com.example.dockb.vo.DocumentVO;
import com.example.dockb.vo.SearchHitVO;
import com.example.dockb.vo.SearchResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private static final List<String> CATEGORY_CANDIDATES = Arrays.asList(
            "技术", "法律", "财务", "医疗", "教育", "商业", "其他", "未分类");

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper chunkMapper;
    private final M3Service m3Service;
    private final AppProperties appProperties;

    @Autowired
    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentChunkMapper chunkMapper,
                               M3Service m3Service,
                               AppProperties appProperties) {
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.m3Service = m3Service;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upload(MultipartFile file, Long ownerId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_EMPTY);
        }
        String original = file.getOriginalFilename();
        if (original == null) {
            original = "unknown";
        }
        String ext = FileUtil.extName(original);
        if (ext == null || ext.isBlank()) {
            ext = "bin";
        }
        String type = ext.toLowerCase();
        if (!TextExtractor.SUPPORTED_TYPES.contains(type)) {
            throw new BizException(ResultCode.FILE_TYPE_INVALID,
                    "不支持的文件类型: " + type + "（仅支持 pdf/txt/md/docx）");
        }

        // 安全校验：魔数验证文件真实类型（使用 mark/reset 避免消耗流）
        InputStream markedStream = new BufferedInputStream(file.getInputStream());
        markedStream.mark(4);
        try {
            TrustedDocuments.validateMagicBytes(markedStream, type);
        } catch (BizException e) {
            throw e;
        }
        try {
            markedStream.reset();
        } catch (IOException e) {
            // 流不支持 reset，重新获取
            markedStream = file.getInputStream();
        }

        // 落盘
        Path dir = ensureUploadDir();
        String stored = IdUtil.simpleUUID() + "." + type;
        Path target = dir.resolve(stored);
        try {
            Files.copy(markedStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("[DocumentService] save file failed", e);
            throw new BizException(ResultCode.UPLOAD_FAILED);
        }

        // 写主表（owner_id = 当前登录用户）
        Document doc = new Document();
        String title = stripExt(original);
        doc.setTitle(title);
        doc.setOriginalName(original);
        doc.setFilePath(target.toAbsolutePath().toString());
        doc.setFileType(type);
        doc.setFileSize(file.getSize());
        doc.setCategory("未分类");
        doc.setTags("");
        doc.setStatus("pending");
        doc.setOwnerId(ownerId); // null = 公开文档
        documentMapper.insert(doc);

        // 同步切块（轻量；失败不影响上传）
        try {
            chunkDocument(doc);
        } catch (Exception e) {
            log.warn("[DocumentService] chunk failed for id={}: {}", doc.getId(), e.getMessage());
        }

        // 异步触发摘要 + 分类 + 标签
        asyncEnrichDocument(doc.getId());

        // 上传后清除分类缓存
        evictCategoryCache();

        return doc.getId();
    }

    @Override
    public PageResult<DocumentVO> page(long page, long size, String keyword, String category) {
        return page(page, size, keyword, category, null, false);
    }

    @Override
    public PageResult<DocumentVO> page(long page, long size, String keyword, String category,
                                      Long userId, boolean isAdmin) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 200) size = 200;

        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        // 可见性：ADMIN 可见所有；USER 可见公开(ownerId=null) + 自己的
        if (!isAdmin) {
            if (userId != null) {
                wrapper.and(w -> w
                        .isNull(Document::getOwnerId)
                        .or()
                        .eq(Document::getOwnerId, userId));
            } else {
                wrapper.isNull(Document::getOwnerId);
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Document::getTitle, keyword.trim());
        }
        if (category != null && !category.isBlank()) {
            wrapper.eq(Document::getCategory, category.trim());
        }
        wrapper.orderByDesc(Document::getCreatedAt);

        Page<Document> result = documentMapper.selectPage(new Page<>(page, size), wrapper);
        List<DocumentVO> list = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public DocumentVO detail(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ResultCode.DOCUMENT_NOT_FOUND);
        }
        return toVO(doc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            // 已删除视为成功
            return;
        }
        // 删主表（外键级联清 chunk）
        documentMapper.deleteById(id);
        // 删物理文件（best-effort）
        try {
            Path p = Paths.get(doc.getFilePath());
            Files.deleteIfExists(p);
        } catch (Exception e) {
            log.warn("[DocumentService] delete file failed for id={}: {}", id, e.getMessage());
        }
    }

    @Override
    public byte[] loadFileBytes(Long id) {
        Document doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new BizException(ResultCode.DOCUMENT_NOT_FOUND);
        }
        try {
            Path p = Paths.get(doc.getFilePath());
            if (!Files.exists(p)) {
                throw new BizException(ResultCode.FILE_READ_FAILED, "文件已被删除");
            }
            return Files.readAllBytes(p);
        } catch (IOException e) {
            log.error("[DocumentService] read file failed", e);
            throw new BizException(ResultCode.FILE_READ_FAILED);
        }
    }

    @Override
    @Cacheable(value = CacheConfig.CATEGORY_CACHE, unless = "#result == null || #result.isEmpty()")
    public List<String> listCategories(Long userId, boolean isAdmin) {
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        if (!isAdmin) {
            if (userId != null) {
                wrapper.and(w -> w.isNull(Document::getOwnerId).or().eq(Document::getOwnerId, userId));
            } else {
                wrapper.isNull(Document::getOwnerId);
            }
        }
        List<Document> all = documentMapper.selectList(wrapper);
        Set<String> set = new HashSet<>();
        for (Document d : all) {
            if (d.getCategory() != null && !d.getCategory().isBlank()) {
                set.add(d.getCategory());
            }
        }
        List<String> out = new ArrayList<>(set);
        Collections.sort(out);
        return out;
    }

    @Override
    @Cacheable(value = CacheConfig.SEARCH_CACHE, key = "#q + ':' + #topK", unless = "#result == null || #result.hits == null || #result.hits.isEmpty()")
    public SearchResponseVO search(String q, int topK) {
        return search(q, topK, null, false);
    }

    @Override
    public SearchResponseVO search(String q, int topK, Long userId, boolean isAdmin) {
        if (q == null || q.isBlank()) {
            throw new BizException(ResultCode.BAD_REQUEST, "查询词 q 不能为空");
        }
        if (topK <= 0) topK = 5;
        if (topK > 20) topK = 20;

        int maxCandidates = appProperties.getSearch().getMaxCandidates();
        // 1) 关键词候选
        List<DocumentChunk> candidates = chunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .like(DocumentChunk::getContent, q.trim())
                        .last("LIMIT " + maxCandidates));
        if (candidates.isEmpty()) {
            return new SearchResponseVO(q, Collections.emptyList());
        }

        // 2) 取文档主表，组装 (documentId, title, category)
        Set<Long> docIds = candidates.stream()
                .map(DocumentChunk::getDocumentId)
                .collect(Collectors.toSet());
        Map<Long, Document> docMap = new HashMap<>();
        if (!docIds.isEmpty()) {
            documentMapper.selectBatchIds(docIds).forEach(d -> docMap.put(d.getId(), d));
        }

        // 2.1) 权限过滤：移除用户无权访问的文档
        List<DocumentChunk> visibleCandidates = candidates.stream()
                .filter(c -> {
                    Document d = docMap.get(c.getDocumentId());
                    if (d == null) return false;
                    if (isAdmin) return true;
                    if (d.getOwnerId() == null) return true; // 公开文档
                    return userId != null && d.getOwnerId().equals(userId);
                })
                .collect(Collectors.toList());
        if (visibleCandidates.isEmpty()) {
            return new SearchResponseVO(q, Collections.emptyList());
        }

        // 3) 重排（带降级）
        List<String> texts = visibleCandidates.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
        List<com.example.dockb.client.dto.RankedHit> ranked =
                m3Service.rerankWithFallback(q, texts);

        List<SearchHitVO> hits = new ArrayList<>();
        if (ranked.isEmpty()) {
            // 极端降级：原序
            for (int i = 0; i < visibleCandidates.size(); i++) {
                hits.add(buildHit(q, visibleCandidates.get(i), docMap.get(visibleCandidates.get(i).getDocumentId()), 0.5));
            }
        } else {
            for (com.example.dockb.client.dto.RankedHit r : ranked) {
                if (r.getIndex() < 0 || r.getIndex() >= visibleCandidates.size()) {
                    continue;
                }
                DocumentChunk c = visibleCandidates.get(r.getIndex());
                Document d = docMap.get(c.getDocumentId());
                hits.add(buildHit(q, c, d, r.getScore()));
                if (hits.size() >= topK) {
                    break;
                }
            }
        }
        return new SearchResponseVO(q, hits);
    }

    @Async("docKbExecutor")
    @Override
    public void asyncEnrichDocument(Long id) {
        try {
            Document doc = documentMapper.selectById(id);
            if (doc == null) {
                return;
            }
            doc.setStatus("processing");
            doc.setErrorMsg(null);
            documentMapper.updateById(doc);

            // 抽取文本
            Path p = Paths.get(doc.getFilePath());
            String text = TextExtractor.extract(p, doc.getFileType());
            // 截断输入防止过长
            String forM3 = text.length() > 8000 ? text.substring(0, 8000) : text;

            String category = m3Service.classifyWithFallback(forM3, CATEGORY_CANDIDATES, "未分类");
            String summary = m3Service.summarizeWithFallback(forM3, defaultSummary(text));
            List<String> tags = m3Service.extractTagsWithFallback(forM3, defaultTags(text));

            doc.setCategory(category == null || category.isBlank() ? "未分类" : category);
            doc.setSummary(summary == null ? "" : summary);
            doc.setTags(tags == null ? "" : String.join(",", tags));
            doc.setStatus("done");
            documentMapper.updateById(doc);
        } catch (Exception e) {
            log.error("[DocumentService] async enrich failed for id={}", id, e);
            Document doc = documentMapper.selectById(id);
            if (doc != null) {
                doc.setStatus("failed");
                String msg = e.getMessage();
                doc.setErrorMsg(msg == null ? "unknown error" : msg.length() > 490 ? msg.substring(0, 490) : msg);
                try {
                    documentMapper.updateById(doc);
                } catch (Exception ex) {
                    log.error("[DocumentService] update failed status failed: {}", ex.getMessage());
                }
            }
        }
    }

    // ----------------------------- 私有工具 -----------------------------

    private SearchHitVO buildHit(String q, DocumentChunk c, Document d, double score) {
        SearchHitVO hit = new SearchHitVO();
        hit.setChunkId(c.getId());
        hit.setDocumentId(c.getDocumentId());
        hit.setTitle(d == null ? null : d.getTitle());
        hit.setCategory(d == null ? null : d.getCategory());
        hit.setScore(score);
        hit.setSnippet(SnippetUtil.snippet(c.getContent(), q, appProperties.getSearch().getSnippetRadius()));
        return hit;
    }

    private Path ensureUploadDir() throws IOException {
        String dir = appProperties.getUploadDir();
        Path p = Paths.get(dir);
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private void chunkDocument(Document doc) {
        Path p = Paths.get(doc.getFilePath());
        String text = TextExtractor.extract(p, doc.getFileType());
        List<String> chunks = TextChunker.chunk(text, appProperties.getChunkSize());
        int idx = 0;
        for (String s : chunks) {
            if (s == null || s.isBlank()) {
                continue;
            }
            DocumentChunk c = new DocumentChunk();
            c.setDocumentId(doc.getId());
            c.setChunkIndex(idx++);
            c.setContent(s);
            c.setCharCount(s.length());
            chunkMapper.insert(c);
        }
    }

    private String defaultSummary(String text) {
        if (text == null) return "";
        String clean = text.replaceAll("\\s+", " ").trim();
        return clean.length() <= 300 ? clean : clean.substring(0, 300);
    }

    private List<String> defaultTags(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        // 取前若干汉字片段作为兜底标签
        String trimmed = text.replaceAll("\\s+", "");
        int len = Math.min(trimmed.length(), 60);
        if (len <= 0) {
            return Collections.emptyList();
        }
        String slice = trimmed.substring(0, len);
        return Arrays.asList(slice.split("(?<=\\G.{6})"));
    }

    private DocumentVO toVO(Document d) {
        DocumentVO vo = new DocumentVO();
        BeanUtils.copyProperties(d, vo);
        return vo;
    }

    /** 清除分类缓存（供 AOP 调用或内部方法触发） */
    @CacheEvict(value = CacheConfig.CATEGORY_CACHE, allEntries = true)
    public void evictCategoryCache() {
        // 注解驱动，方法体为空
    }
}