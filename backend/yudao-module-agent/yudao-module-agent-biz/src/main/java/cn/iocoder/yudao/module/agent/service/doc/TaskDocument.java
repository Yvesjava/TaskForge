package cn.iocoder.yudao.module.agent.service.doc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析后的任务文档
 *
 * <p>包含类型化的 YAML Front Matter 以及去除 Front Matter 后的 Markdown 正文。
 *
 * @author TaskForge
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDocument {

    /**
     * 类型化的 Front Matter
     */
    private TaskFrontMatter frontMatter;

    /**
     * 去除 Front Matter 后的 Markdown 正文
     */
    private String body;

}
