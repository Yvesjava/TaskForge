package cn.iocoder.yudao.module.agent.framework.git.platform;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Git 仓库地址解析器。
 *
 * <p>支持 {@code https://}、{@code http://}、{@code ssh://}、{@code git://} 以及
 * SCP 风格（{@code git@host:owner/repo.git}）地址，解析出平台、主机与 owner/name。
 * 平台默认按主机名推断；自建实例无法从主机名判断时，可传入显式平台。</p>
 *
 * @author TaskForge
 */
public final class GitRepositoryParser {

    private GitRepositoryParser() {
    }

    public static GitRepository parse(String gitUrl) {
        return parse(gitUrl, null);
    }

    public static GitRepository parse(String gitUrl, GitPlatform explicitPlatform) {
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("Git 仓库地址不能为空");
        }
        String value = gitUrl.trim();
        String host;
        String path;
        if (value.startsWith("git@")) {
            int separator = value.indexOf(':');
            if (separator <= "git@".length()) {
                throw new IllegalArgumentException("无法解析 Git 仓库地址：" + gitUrl);
            }
            host = value.substring("git@".length(), separator);
            path = value.substring(separator + 1);
        } else {
            URI uri;
            try {
                uri = URI.create(value);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("无法解析 Git 仓库地址：" + gitUrl, e);
            }
            if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new IllegalArgumentException("无法解析 Git 仓库地址：" + gitUrl);
            }
            host = uri.getHost();
            path = uri.getPath();
        }

        GitPlatform platform = explicitPlatform == null ? detectPlatform(host) : explicitPlatform;
        List<String> segments = normalizeSegments(path);
        if (segments.size() < 2) {
            throw new IllegalArgumentException("Git 仓库地址缺少 owner/repo 信息：" + gitUrl);
        }
        String name = segments.get(segments.size() - 1);
        String owner = String.join("/", segments.subList(0, segments.size() - 1));
        return GitRepository.of(platform, host, owner, name);
    }

    private static GitPlatform detectPlatform(String host) {
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ("github.com".equals(lowerHost) || lowerHost.endsWith(".github.com")) {
            return GitPlatform.GITHUB;
        }
        if (lowerHost.contains("gitlab")) {
            return GitPlatform.GITLAB;
        }
        if (lowerHost.contains("gitea")) {
            return GitPlatform.GITEA;
        }
        throw new IllegalArgumentException("无法识别 Git 平台，仅支持 GitHub/GitLab/Gitea 仓库地址：" + host);
    }

    private static List<String> normalizeSegments(String path) {
        String normalized = path;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - ".git".length());
        }
        List<String> segments = new ArrayList<>();
        for (String part : normalized.split("/")) {
            if (part.isEmpty()) {
                continue;
            }
            if (".".equals(part) || "..".equals(part)) {
                throw new IllegalArgumentException("Git 仓库地址包含非法路径段：" + path);
            }
            segments.add(part);
        }
        return segments;
    }

}
