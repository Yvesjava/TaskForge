package cn.iocoder.yudao.module.agent.service.scheduler;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Worker 进程身份标识
 *
 * <p>租约、心跳与执行代次都以本标识区分不同 Worker，避免旧 Worker 覆盖新结果。</p>
 *
 * @author TaskForge
 */
@Component
public class WorkerIdentity {

    private final String id;

    public WorkerIdentity() {
        this.id = resolveHostname() + ":" + ProcessHandle.current().pid();
    }

    public String id() {
        return id;
    }

    private static String resolveHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "worker";
        }
    }

}
