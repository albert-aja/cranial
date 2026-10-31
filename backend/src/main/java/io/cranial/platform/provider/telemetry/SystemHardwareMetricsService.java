package io.cranial.platform.provider.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SystemHardwareMetricsService {

    private static final Logger log = LoggerFactory.getLogger(SystemHardwareMetricsService.class);

    public record GpuUtilization(
            String name,
            Double utilizationGpuPercent,
            Double utilizationMemoryPercent,
            Long memoryUsedBytes,
            Long memoryTotalBytes,
            Double temperatureCelsius,
            String source
    ) {}

    public record HardwareUtilizationSnapshot(
            double cpuProcessLoadPercent,
            double cpuSystemLoadPercent,
            int availableProcessors,
            long jvmUsedMemoryBytes,
            long jvmMaxMemoryBytes,
            long systemUsedMemoryBytes,
            long systemTotalMemoryBytes,
            double systemMemoryUsedPercent,
            List<GpuUtilization> gpus,
            Instant timestamp
    ) {}

    public HardwareUtilizationSnapshot getSnapshot() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int processors = osBean.getAvailableProcessors();

        double cpuProc = -1.0;
        double cpuSys = -1.0;
        long totalPhys = -1L;
        long freePhys = -1L;

        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            cpuProc = sunBean.getProcessCpuLoad() >= 0 ? roundTwoDecimals(sunBean.getProcessCpuLoad() * 100.0) : -1.0;
            cpuSys = sunBean.getCpuLoad() >= 0 ? roundTwoDecimals(sunBean.getCpuLoad() * 100.0) : -1.0;
            totalPhys = sunBean.getTotalMemorySize();
            freePhys = sunBean.getFreeMemorySize();
        }

        Runtime runtime = Runtime.getRuntime();
        long jvmTotal = runtime.totalMemory();
        long jvmFree = runtime.freeMemory();
        long jvmUsed = jvmTotal - jvmFree;
        long jvmMax = runtime.maxMemory();

        long sysUsed = -1L;
        double sysMemPercent = -1.0;
        if (totalPhys > 0 && freePhys >= 0) {
            sysUsed = totalPhys - freePhys;
            sysMemPercent = roundTwoDecimals(((double) sysUsed / totalPhys) * 100.0);
        }

        List<GpuUtilization> gpus = detectGpus();

        return new HardwareUtilizationSnapshot(
                cpuProc,
                cpuSys,
                processors,
                jvmUsed,
                jvmMax,
                sysUsed,
                totalPhys,
                sysMemPercent,
                gpus,
                Instant.now()
        );
    }

    private List<GpuUtilization> detectGpus() {
        List<GpuUtilization> gpus = new ArrayList<>();
        try {
            gpus.addAll(detectNvidiaGpus());
        } catch (Exception ex) {
            log.debug("NVIDIA GPU detection skipped: {}", ex.getMessage());
        }
        return gpus;
    }

    private List<GpuUtilization> detectNvidiaGpus() {
        List<GpuUtilization> list = new ArrayList<>();
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "nvidia-smi",
                    "--query-gpu=name,utilization.gpu,utilization.memory,memory.used,memory.total,temperature.gpu",
                    "--format=csv,noheader,nounits"
            );
            pb.redirectErrorStream(true);
            process = pb.start();

            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return list;
            }

            if (process.exitValue() == 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isBlank() || line.startsWith("[") || line.startsWith("nvidia-smi")) {
                            continue;
                        }
                        String[] parts = line.split(",");
                        if (parts.length >= 6) {
                            String name = parts[0].trim();
                            Double utilGpu = parseDoubleOrNull(parts[1].trim());
                            Double utilMem = parseDoubleOrNull(parts[2].trim());
                            Long memUsedMb = parseLongOrNull(parts[3].trim());
                            Long memTotalMb = parseLongOrNull(parts[4].trim());
                            Double temp = parseDoubleOrNull(parts[5].trim());

                            Long memUsedBytes = memUsedMb != null ? memUsedMb * 1024L * 1024L : null;
                            Long memTotalBytes = memTotalMb != null ? memTotalMb * 1024L * 1024L : null;

                            list.add(new GpuUtilization(
                                    name,
                                    utilGpu,
                                    utilMem,
                                    memUsedBytes,
                                    memTotalBytes,
                                    temp,
                                    "nvidia-smi"
                            ));
                        }
                    }
                }
            }
        } catch (Exception ex) {
            // nvidia-smi not available or failed
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        return list;
    }

    private static Double parseDoubleOrNull(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLongOrNull(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static double roundTwoDecimals(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

