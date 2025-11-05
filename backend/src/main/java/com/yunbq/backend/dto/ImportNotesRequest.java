package com.yunbq.backend.dto;

import lombok.Data;
import java.util.List;

/**
 * 导入拾言请求体
 * 说明：
 * - 与前端约定的结构一致：{ items: NoteRequest[] }；
 * - 每个 NoteRequest 包含 content/tags/color/archived/isPublic 字段；
 * - 可后续扩展 dryRun/skipInvalid 等控制参数（当前不需要，避免过度设计）。
 */
@Data
public class ImportNotesRequest {
    /**
     * 待导入的拾言条目列表
     * 约束：列表可为空；服务层将进行字段最小校验（content 非空）与失败统计。
     */
    private List<NoteRequest> items;
}