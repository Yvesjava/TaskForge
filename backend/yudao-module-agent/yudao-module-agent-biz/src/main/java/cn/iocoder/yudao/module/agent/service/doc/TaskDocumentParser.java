package cn.iocoder.yudao.module.agent.service.doc;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_EMPTY;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FIELD_TYPE_ERROR;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FRONT_MATTER_EMPTY;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FRONT_MATTER_INVALID;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_CLOSED;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_FOUND;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_FRONT_MATTER_NOT_MAPPING;

/**
 * 任务文档 YAML Front Matter 解析器
 *
 * <p>职责限定为解析与字段类型转换：从 Markdown 顶部提取 YAML Front Matter，
 * 将其转换为 {@link TaskFrontMatter} 类型化模型，并支持单仓（{@code repoUrl}/{@code baseBranch}）
 * 与多仓（{@code projects} 列表）两种示例。类型不匹配时抛出携带字段路径与行/列位置的异常，
 * 必填性与业务校验由后续文档校验任务负责。
 *
 * @author TaskForge
 */
@Component
public class TaskDocumentParser {

    private static final String FRONT_MATTER_DELIMITER = "---";

    /**
     * 解析任务 Markdown 文档，返回类型化 Front Matter 与正文。
     *
     * @param markdown 完整任务文档
     * @return 解析后的任务文档
     * @throws ServiceException 文档为空、缺少 Front Matter 或字段类型错误时抛出
     */
    public TaskDocument parse(String markdown) {
        if (markdown == null || markdown.trim().isEmpty()) {
            throw exception(DOCUMENT_EMPTY);
        }

        String text = stripBom(markdown);
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\r?\\n", -1)));

        int opening = findOpeningDelimiter(lines);
        if (opening < 0) {
            throw exception(DOCUMENT_FRONT_MATTER_NOT_FOUND);
        }
        int closing = findClosingDelimiter(lines, opening);
        if (closing < 0) {
            throw exception(DOCUMENT_FRONT_MATTER_NOT_CLOSED);
        }

        String yaml = String.join("\n", lines.subList(opening + 1, closing));
        // opening 为 0 基索引；首个内容行在文档中的 1 基行号为 opening + 2。
        int lineOffset = opening + 2;
        Node root = compose(yaml, lineOffset);

        TaskFrontMatter frontMatter = convertFrontMatter(root, lineOffset);
        String body = String.join("\n", lines.subList(closing + 1, lines.size()));
        return TaskDocument.builder()
                .frontMatter(frontMatter)
                .body(body)
                .build();
    }

    private int findOpeningDelimiter(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.trim().isEmpty() && !FRONT_MATTER_DELIMITER.equals(line.trim())) {
                // 首个非空行不是分隔符，说明没有 Front Matter
                return -1;
            }
            if (FRONT_MATTER_DELIMITER.equals(line.trim())) {
                return i;
            }
        }
        return -1;
    }

    private int findClosingDelimiter(List<String> lines, int opening) {
        for (int i = opening + 1; i < lines.size(); i++) {
            if (FRONT_MATTER_DELIMITER.equals(lines.get(i).trim())) {
                return i;
            }
        }
        return -1;
    }

    private Node compose(String yaml, int lineOffset) {
        if (yaml.trim().isEmpty()) {
            throw exception(DOCUMENT_FRONT_MATTER_EMPTY);
        }
        try {
            Node root = new Yaml().compose(new StringReader(yaml));
            if (root == null) {
                throw exception(DOCUMENT_FRONT_MATTER_EMPTY);
            }
            return root;
        } catch (MarkedYAMLException ex) {
            throw exception(DOCUMENT_FRONT_MATTER_INVALID, formatYamlProblem(ex, lineOffset));
        } catch (YAMLException ex) {
            throw exception(DOCUMENT_FRONT_MATTER_INVALID, ex.getMessage());
        }
    }

    private String formatYamlProblem(MarkedYAMLException ex, int lineOffset) {
        Mark mark = ex.getProblemMark();
        String problem = ex.getProblem() == null ? ex.getMessage() : ex.getProblem();
        if (mark == null) {
            return problem;
        }
        return "第 " + (lineOffset + mark.getLine()) + " 行第 " + (mark.getColumn() + 1) + " 列：" + problem;
    }

    private TaskFrontMatter convertFrontMatter(Node root, int lineOffset) {
        if (!(root instanceof MappingNode mapping)) {
            throw exception(DOCUMENT_FRONT_MATTER_NOT_MAPPING);
        }

        Map<String, Node> nodes = toNodeMap(mapping);
        TaskFrontMatter frontMatter = new TaskFrontMatter();
        if (nodes.containsKey("taskId")) {
            frontMatter.setTaskId(stringValue(nodes.get("taskId"), "taskId", lineOffset));
        }
        if (nodes.containsKey("title")) {
            frontMatter.setTitle(stringValue(nodes.get("title"), "title", lineOffset));
        }
        if (nodes.containsKey("targetBranch")) {
            frontMatter.setTargetBranch(stringValue(nodes.get("targetBranch"), "targetBranch", lineOffset));
        }
        if (nodes.containsKey("timeoutMinutes")) {
            frontMatter.setTimeoutMinutes(intValue(nodes.get("timeoutMinutes"), "timeoutMinutes", lineOffset));
        }
        if (nodes.containsKey("priority")) {
            frontMatter.setPriority(intValue(nodes.get("priority"), "priority", lineOffset));
        }
        if (nodes.containsKey("dependsOnTaskId")) {
            frontMatter.setDependsOnTaskId(longValue(nodes.get("dependsOnTaskId"), "dependsOnTaskId", lineOffset));
        }
        if (nodes.containsKey("repoUrl")) {
            frontMatter.setRepoUrl(stringValue(nodes.get("repoUrl"), "repoUrl", lineOffset));
        }
        if (nodes.containsKey("baseBranch")) {
            frontMatter.setBaseBranch(stringValue(nodes.get("baseBranch"), "baseBranch", lineOffset));
        }
        if (nodes.containsKey("projects")) {
            frontMatter.setProjects(projectsValue(nodes.get("projects"), "projects", lineOffset));
        }
        return frontMatter;
    }

    private Map<String, Node> toNodeMap(MappingNode mapping) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (NodeTuple tuple : mapping.getValue()) {
            Node keyNode = tuple.getKeyNode();
            if (keyNode instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())) {
                nodes.put(scalar.getValue(), tuple.getValueNode());
            }
        }
        return nodes;
    }

    private String stringValue(Node node, String path, int lineOffset) {
        if (node instanceof ScalarNode scalar) {
            if (Tag.NULL.equals(scalar.getTag())) {
                return null;
            }
            if (Tag.STR.equals(scalar.getTag())) {
                return scalar.getValue();
            }
        }
        throw typeError(path, node, lineOffset, "应为字符串");
    }

    private Integer intValue(Node node, String path, int lineOffset) {
        if (node instanceof ScalarNode scalar) {
            if (Tag.NULL.equals(scalar.getTag())) {
                return null;
            }
            if (Tag.INT.equals(scalar.getTag())) {
                try {
                    return Integer.valueOf(parseIntText(scalar.getValue()));
                } catch (NumberFormatException ex) {
                    throw typeError(path, scalar, lineOffset, "应为整数");
                }
            }
        }
        throw typeError(path, node, lineOffset, "应为整数");
    }

    private Long longValue(Node node, String path, int lineOffset) {
        if (node instanceof ScalarNode scalar) {
            if (Tag.NULL.equals(scalar.getTag())) {
                return null;
            }
            if (Tag.INT.equals(scalar.getTag())) {
                try {
                    return Long.valueOf(parseIntText(scalar.getValue()));
                } catch (NumberFormatException ex) {
                    throw typeError(path, scalar, lineOffset, "应为整数");
                }
            }
        }
        throw typeError(path, node, lineOffset, "应为整数");
    }

    private List<TaskProjectRef> projectsValue(Node node, String path, int lineOffset) {
        if (node instanceof ScalarNode scalar && Tag.NULL.equals(scalar.getTag())) {
            return null;
        }
        if (!(node instanceof SequenceNode sequence)) {
            throw typeError(path, node, lineOffset, "应为项目列表");
        }

        List<TaskProjectRef> projects = new ArrayList<>();
        List<Node> items = sequence.getValue();
        for (int i = 0; i < items.size(); i++) {
            projects.add(projectValue(items.get(i), path + "[" + i + "]", lineOffset));
        }
        return projects;
    }

    private TaskProjectRef projectValue(Node node, String path, int lineOffset) {
        if (!(node instanceof MappingNode mapping)) {
            throw typeError(path, node, lineOffset, "应为键值对映射");
        }
        Map<String, Node> nodes = toNodeMap(mapping);
        TaskProjectRef ref = new TaskProjectRef();
        if (nodes.containsKey("code")) {
            ref.setCode(stringValue(nodes.get("code"), path + ".code", lineOffset));
        }
        if (nodes.containsKey("baseBranch")) {
            ref.setBaseBranch(stringValue(nodes.get("baseBranch"), path + ".baseBranch", lineOffset));
        }
        if (nodes.containsKey("subDir")) {
            ref.setSubDir(stringValue(nodes.get("subDir"), path + ".subDir", lineOffset));
        }
        return ref;
    }

    /**
     * 解析十进制整数文本；兼容 YAML 中可读性下划线（如 {@code 1_000}）。
     */
    private String parseIntText(String value) {
        return value == null ? null : value.replace("_", "");
    }

    private ServiceException typeError(String path, Node node, int lineOffset, String reason) {
        Mark mark = node.getStartMark();
        if (mark == null) {
            return exception(DOCUMENT_FIELD_TYPE_ERROR, path, lineOffset, 0, reason);
        }
        int line = lineOffset + mark.getLine();
        int column = mark.getColumn() + 1;
        return exception(DOCUMENT_FIELD_TYPE_ERROR, path, line, column, reason);
    }

    private String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\uFEFF') {
            return text.substring(1);
        }
        return text;
    }

}
