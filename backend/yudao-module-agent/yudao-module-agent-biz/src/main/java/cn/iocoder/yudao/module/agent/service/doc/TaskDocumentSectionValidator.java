package cn.iocoder.yudao.module.agent.service.doc;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_EMPTY;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_SECTION_EMPTY;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_SECTION_MISSING;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_SECTION_NOT_CHECKLIST;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_SECTION_STEP_NOT_EXECUTABLE;
import static cn.iocoder.yudao.module.agent.enums.ErrorCodeConstants.DOCUMENT_SECTION_STEP_NOT_FOUND;

/**
 * 任务文档正文小节校验器
 *
 * <p>校验正文必须包含需求目标与上下文、任务执行计划、验收步骤和验收标准四个标准小节；
 * 其中执行计划与验收标准要求使用可勾选清单，验收步骤要求至少包含一个可执行命令或明确检查项。
 *
 * @author TaskForge
 */
@Component
public class TaskDocumentSectionValidator {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Pattern FENCE_PATTERN = Pattern.compile("^\\s{0,3}(```+|~~~+)");
    private static final Pattern CHECKBOX_PATTERN = Pattern.compile("^\\s*[-*+]\\s+\\[[ xX]\\]\\s*");
    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^\\s*(?:\\d+[.)]|[-*+])\\s+(.+)$");
    private static final Pattern COMMAND_PATTERN = Pattern.compile(
            "\\b(cd|mvn|mvnw|gradle|npm|pnpm|yarn|make|curl|wget|git|docker|java|python|pytest|go|dotnet)\\b");

    /**
     * 校验已解析任务文档的正文小节。
     *
     * @param document 已解析的任务文档
     */
    public void validate(TaskDocument document) {
        if (document == null) {
            throw exception(DOCUMENT_EMPTY);
        }
        validate(document.getBody());
    }

    /**
     * 校验 Markdown 正文小节。
     *
     * @param body 去除 Front Matter 后的 Markdown 正文
     */
    public void validate(String body) {
        if (body == null || body.trim().isEmpty()) {
            throw exception(DOCUMENT_EMPTY);
        }

        List<String> lines = Arrays.asList(body.split("\\r?\\n", -1));
        List<Heading> headings = findHeadings(lines);
        Map<Section, Heading> requiredHeadings = locateRequiredHeadings(headings);

        for (Section section : Section.requiredSections()) {
            Heading heading = requiredHeadings.get(section);
            if (heading == null) {
                throw exception(DOCUMENT_SECTION_MISSING, section.displayName());
            }

            List<String> content = contentBetween(lines, heading, headings);
            if (content.stream().allMatch(String::isBlank)) {
                throw exception(DOCUMENT_SECTION_EMPTY, section.displayName());
            }

            switch (section) {
                case PLAN, CRITERIA -> requireChecklist(section, content);
                case STEPS -> requireExecutableSteps(section, content);
                default -> {
                    // 需求目标与上下文仅要求存在且非空
                }
            }
        }
    }

    private List<Heading> findHeadings(List<String> lines) {
        List<Heading> headings = new ArrayList<>();
        boolean inFence = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isFence = FENCE_PATTERN.matcher(line).find();
            if (isFence) {
                inFence = !inFence;
                continue;
            }
            if (inFence) {
                continue;
            }

            Matcher matcher = HEADING_PATTERN.matcher(line);
            if (matcher.matches()) {
                headings.add(new Heading(i, matcher.group(2), matcher.group(1).length()));
            }
        }
        return headings;
    }

    private Map<Section, Heading> locateRequiredHeadings(List<Heading> headings) {
        Map<Section, Heading> result = new EnumMap<>(Section.class);
        for (Heading heading : headings) {
            Section section = Section.recognize(heading.title());
            if (section != null && !result.containsKey(section)) {
                result.put(section, heading);
            }
        }
        return result;
    }

    private List<String> contentBetween(List<String> lines, Heading heading, List<Heading> headings) {
        int end = lines.size();
        for (Heading candidate : headings) {
            boolean sameOrHigherLevel = candidate.level() <= heading.level();
            boolean requiredSectionBoundary = Section.recognize(candidate.title()) != null;
            if (candidate.index() > heading.index() && (sameOrHigherLevel || requiredSectionBoundary)) {
                end = candidate.index();
                break;
            }
        }
        if (heading.index() + 1 >= end) {
            return List.of();
        }
        return new ArrayList<>(lines.subList(heading.index() + 1, end));
    }

    private void requireChecklist(Section section, List<String> content) {
        boolean hasCheckbox = content.stream().anyMatch(line -> CHECKBOX_PATTERN.matcher(line).find());
        if (!hasCheckbox) {
            throw exception(DOCUMENT_SECTION_NOT_CHECKLIST, section.displayName());
        }
    }

    private void requireExecutableSteps(Section section, List<String> content) {
        List<String> items = new ArrayList<>();
        for (String line : content) {
            Matcher matcher = LIST_ITEM_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            if (matcher.group(1).trim().isEmpty()) {
                throw exception(DOCUMENT_SECTION_STEP_NOT_EXECUTABLE, section.displayName());
            }
            items.add(line);
        }

        if (items.isEmpty()) {
            throw exception(DOCUMENT_SECTION_STEP_NOT_FOUND, section.displayName());
        }
        if (items.stream().noneMatch(this::isExecutableStep)) {
            throw exception(DOCUMENT_SECTION_STEP_NOT_EXECUTABLE, section.displayName());
        }
    }

    private boolean isExecutableStep(String line) {
        if (CHECKBOX_PATTERN.matcher(line).find()) {
            return true;
        }
        if (line.contains("`")) {
            return true;
        }
        return COMMAND_PATTERN.matcher(line.toLowerCase(Locale.ROOT)).find();
    }

    private enum Section {

        GOAL("需求目标与上下文"),
        PLAN("任务执行计划"),
        STEPS("验收步骤"),
        CRITERIA("验收标准");

        private final String displayName;

        Section(String displayName) {
            this.displayName = displayName;
        }

        String displayName() {
            return displayName;
        }

        static List<Section> requiredSections() {
            return List.of(GOAL, PLAN, STEPS, CRITERIA);
        }

        static Section recognize(String title) {
            String normalized = normalize(title);
            if (containsAny(normalized, "需求目标", "目标与上下文")) {
                return GOAL;
            }
            if (containsAny(normalized, "执行计划", "execution plan")) {
                return PLAN;
            }
            if (containsAny(normalized, "验收步骤", "verification steps")) {
                return STEPS;
            }
            if (containsAny(normalized, "验收标准", "acceptance criteria")) {
                return CRITERIA;
            }
            return null;
        }

        private static String normalize(String title) {
            return title == null ? "" : title.replace("`", "").toLowerCase(Locale.ROOT);
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Heading(int index, String title, int level) {
    }

}
