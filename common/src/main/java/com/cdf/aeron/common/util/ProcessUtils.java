package com.cdf.aeron.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

/**
 * @author chendifan
 * @date 2024-09-01
 */
@Slf4j
public class ProcessUtils {
    private static final String SPLITTER = " ";

    private ProcessUtils() {
    }

    public static void ls(File file) {
        if (isNull(file)) {
            log.warn("file is null");
            return;
        }
        String absolutePath = file.getAbsolutePath();
        if (Files.notExists(file.toPath())) {
            log.warn("file not exists, path: {}", absolutePath);
            return;
        }
        exec("ls -al " + absolutePath);
    }

    public static void tree(File file) {
        if (isNull(file)) {
            log.warn("file is null");
            return;
        }
        String absolutePath = file.getAbsolutePath();
        if (Files.notExists(file.toPath())) {
            log.warn("file not exists, path: {}", absolutePath);
            return;
        }
        exec("tree " + absolutePath);
    }

    public static void lsofUdp() {
        long pid = ProcessHandle.current().pid();
        exec("lsof -p " + pid + " -P -a -iUDP");
    }

    private static void exec(String cmd) {
        log.info("exec: {}", cmd);
        String[] cmdArr = cmd.split(SPLITTER);
        BufferedReader stdin = null;
        BufferedReader stderr = null;
        try {
            Process pp = Runtime.getRuntime().exec(cmdArr);
            pp.waitFor();
            stdin = pp.inputReader();
            stderr = pp.errorReader();
            if (log.isInfoEnabled() && stdin.ready()) {
                log.info(stdin.lines().collect(Collectors.joining("\n")));
            }
            if (log.isErrorEnabled() && stderr.ready()) {
                log.error(stderr.lines().collect(Collectors.joining("\n")));
            }
        } catch (IOException e) {
            log.error("failed to execute command \"{}\"", cmd, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("failed to execute command \"{}\"", cmd, e);
        } finally {
            if (stdin != null) {
                try {
                    stdin.close();
                } catch (IOException e) {
                    log.error("failed to close stdin", e);
                }
            }
            if (stderr != null) {
                try {
                    stderr.close();
                } catch (IOException e) {
                    log.error("failed to close stderr", e);
                }
            }
        }
    }
}
