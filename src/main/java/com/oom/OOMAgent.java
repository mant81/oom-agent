package com.oom;

import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.sql.*;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class OOMAgent {

    // ===== 설정 =====
    private static boolean USE_DB = true;
    private static String DB_URL = "jdbc:mysql://localhost:3306/monitor";
    private static String DB_USER = "user";
    private static String DB_PASSWORD = "password";

    private static boolean USE_LOG = true;
    private static String LOG_FILE = "oom_agent.log";

    private static long CHECK_INTERVAL_MS = 1000;
    private static double HEAP_THRESHOLD = 80.0;
    private static double OOM_THRESHOLD_MS = 30000;

    private static boolean headerPrinted = false;

    // ===== 진입점 =====
    public static void premain(String agentArgs, Instrumentation inst) {
        safePrint("[OOMAgent] Agent started");

        parseArgs(agentArgs);

        Thread monitorThread = new Thread(() -> monitorHeap(), "OOMAgent-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    // ===== 힙 모니터링 =====
    private static void monitorHeap() {
        try (PrintWriter writer = USE_LOG ? new PrintWriter(new FileWriter(LOG_FILE, true)) : null) {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long prevUsed = 0;
            long prevTime = System.currentTimeMillis();

            while (true) {
                MemoryUsage heap = memoryBean.getHeapMemoryUsage();
                long used = heap.getUsed();
                long max = heap.getMax();
                double usagePercent = (double) used / max * 100;

                long currentTime = System.currentTimeMillis();
                long elapsedMs = currentTime - prevTime;
                double slope = (double) (used - prevUsed) / (elapsedMs == 0 ? 1 : elapsedMs);
                double bytesToOOM = max - used;
                double msToOOM = slope > 0 ? bytesToOOM / slope : Double.POSITIVE_INFINITY;

                if (!headerPrinted && USE_LOG && writer != null) {
                    printHeader(writer);
                    headerPrinted = true;
                }

                log(used, max, msToOOM, HEAP_THRESHOLD, OOM_THRESHOLD_MS, writer);

                //if (USE_DB && (usagePercent >= HEAP_THRESHOLD || msToOOM <= OOM_THRESHOLD_MS)) {
                	//logToDatabase(used, max, usagePercent, msToOOM);
                //}
                
                if (USE_DB) {                 
                    logToDatabase(used, max, msToOOM, HEAP_THRESHOLD, OOM_THRESHOLD_MS);
                }

                prevUsed = used;
                prevTime = currentTime;
                Thread.sleep(CHECK_INTERVAL_MS);
            }
        } catch (InterruptedException | IOException e) {
            safePrint("[OOMAgent] Monitoring thread stopped: " + e.getMessage());
        }
    }

    // ===== 로그 출력 =====
    private static void printHeader(PrintWriter writer) {
        String header = "Timestamp | MaxHeap(MB) | UsedHeap(MB) | Remaining(MB) | Usage(%) | HeapThreshold(%) | EstOOM | OOMThreshold | Status";
        safePrint(header);
        if (writer != null) {
            writer.println(header);
            writer.flush();
        }
    }

    private static void log(long used, long max, double msToOOM, double heapThreshold, double oomThresholdMs, PrintWriter writer) {
        double usagePercent = (double) used / max * 100;
        long remaining = max - used;
        String status = (usagePercent >= heapThreshold || msToOOM <= oomThresholdMs) ? "위험" : "정상";

        String estOOM = formatOOMTime(msToOOM);
        String oomWarnLimit = formatOOMTime(oomThresholdMs) + " (기준)";

        String log = String.format(
            "%tF %<tT | %d | %d | %d | %.0f%% | %.0f%% | %s | %s | %s",
            System.currentTimeMillis(),
            max / (1024 * 1024),
            used / (1024 * 1024),
            remaining / (1024 * 1024),
            usagePercent,
            heapThreshold,
            estOOM,
            oomWarnLimit,
            status
        );

        safePrint("[OOMAgent] " + log);
        if (writer != null) {
            writer.println(log);
            writer.flush();
        }
    }

    // ===== DB 로그 =====
    private static void logToDatabase(long used, long max, double msToOOM, double heapThreshold, double oomThresholdMs) {
        double usagePercent = (double) used / max * 100;
        long remaining = max - used;
        String status = (usagePercent >= heapThreshold || msToOOM <= oomThresholdMs) ? "위험" : "정상";
        String estOOM = formatOOMTime(msToOOM);
        String oomWarnLimit = formatOOMTime(oomThresholdMs) + " (기준)";

        try {
            Class.forName("com.mysql.jdbc.Driver");
            String insertSQL = "INSERT INTO oom_logs (" +
                    "timestamp, max_heap_mb, used_heap_mb, remaining_mb, usage_percent, " +
                    "heap_threshold, est_oom, oom_threshold, status, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement ps = conn.prepareStatement(insertSQL)) {

                ps.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
                ps.setLong(2, max / (1024 * 1024));
                ps.setLong(3, used / (1024 * 1024));
                ps.setLong(4, remaining / (1024 * 1024));
                ps.setDouble(5, usagePercent);
                ps.setDouble(6, heapThreshold);
                ps.setString(7, estOOM);
                ps.setString(8, oomWarnLimit);
                ps.setString(9, status);
                ps.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // ===== 설정 파싱 =====
    private static void parseArgs(String agentArgs) {
        if (agentArgs == null) return;
        String[] args = agentArgs.split(";");
        for (String arg : args) {
            String[] kv = arg.split("=");
            if (kv.length != 2) continue;
            String key = kv[0].trim(), value = kv[1].trim();
            try {
                switch (key) {
                    case "interval": CHECK_INTERVAL_MS = Long.parseLong(value); break;
                    case "heap": HEAP_THRESHOLD = Double.parseDouble(value); break;
                    case "oom": OOM_THRESHOLD_MS = Double.parseDouble(value); break;
                    case "log": LOG_FILE = value; break;
                    case "use_db": USE_DB = Boolean.parseBoolean(value); break;
                    case "use_log": USE_LOG = Boolean.parseBoolean(value); break;
                    case "db_url": DB_URL = value; break;
                    case "db_user": DB_USER = value; break;
                    case "db_pass": DB_PASSWORD = value; break;
                }
            } catch (Exception e) {
                safePrint("[OOMAgent] Invalid arg: " + arg);
            }
        }
    }

    // ===== 안전 출력 (Logback과 완전 분리) =====
    private static void safePrint(String msg) {
        try {
            System.err.println(msg);
        } catch (Throwable ignored) {}
    }

    // ===== 시간 포맷 =====
    private static String formatOOMTime(double msToOOM) {
        if (Double.isInfinite(msToOOM) || msToOOM > 31_536_000_000.0) return "∞ (무한)";
        long sec = (long) (msToOOM / 1000);
        if (sec < 60) return sec + " sec";
        if (sec < 3600) return (sec / 60) + " min";
        if (sec < 86400) return (sec / 3600) + " hr";
        return (sec / 86400) + " day";
    }
}
