/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.yardstick.probes;

import org.yardstick.*;
import org.yardstick.util.*;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

/**
 * Probe that gathers statistics generated by Linux 'dstat' command.
 */
public class DStatProbe implements BenchmarkProbe {
    /** */
    private static final String PATH = "benchmark.probe.dstat.path";

    /** */
    private static final String OPTS = "benchmark.probe.dstat.opts";

    /** */
    private static final int DEFAULT_INVERVAL_IN_SECS = 1;

    /** */
    private static final String DEFAULT_PATH = "dstat";

    /** */
    private static final String DEFAULT_OPTS = "--all --noheaders --noupdate " + DEFAULT_INVERVAL_IN_SECS;

    /** */
    private static final String FIRST_LINE_RE =
        "-*total-cpu-usage-* -*dsk/total-* -*net/total-* -*paging-* -*system-*\\s*$";

    /** */
    private static final Pattern FIRST_LINE = Pattern.compile(FIRST_LINE_RE);

    /** */
    private static final String HEADER_LINE_RE = "^\\s*usr\\s+sys\\s+idl\\s+wai\\s+hiq\\s+siq\\s+read\\s+writ" +
        "\\s+recv\\s+send\\s+in\\s+out\\s+int\\s+csw\\s*";

    /** */
    private static final Pattern HEADER_LINE = Pattern.compile(HEADER_LINE_RE);

    /** */
    private static final Pattern DSTAT_PAT;

    static {
        int numFields = 14;

        StringBuilder sb = new StringBuilder("^\\s*");

        for (int i = 0; i < numFields; i++) {
            sb.append("(\\d+)");

            if (i < numFields - 1)
                sb.append("\\s+");
            else
                sb.append("\\s*");
        }

        DSTAT_PAT = Pattern.compile(sb.toString());
    }

    /** */
    private BenchmarkConfiguration cfg;

    /** */
    private BenchmarkProcessLauncher proc;

    /** Collected points. */
    private Collection<BenchmarkProbePoint> collected = new ArrayList<>();

    /** {@inheritDoc} */
    @Override public void start(BenchmarkConfiguration cfg) throws Exception {
        this.cfg = cfg;

        BenchmarkClosure<String> c = new BenchmarkClosure<String>() {
            private final AtomicInteger lineNum = new AtomicInteger(0);

            @Override public void apply(String s) {
                parseLine(lineNum.getAndIncrement(), s);
            }
        };

        proc = new BenchmarkProcessLauncher();

        Collection<String> cmdParams = new ArrayList<>();

        cmdParams.add(path(cfg));
        cmdParams.addAll(opts(cfg));

        try {
            proc.exec(cmdParams, Collections.<String, String>emptyMap(), c);
        }
        catch (Exception e) {
            cfg.error().println("Can not start 'dstat' process due to exception: " + e.getMessage());
        }

        cfg.output().println(DStatProbe.class.getSimpleName() + " is started.");
    }

    /** {@inheritDoc} */
    @Override public void stop() throws Exception {
        if (proc != null) {
            proc.shutdown(false);

            cfg.output().println(DStatProbe.class.getSimpleName() + " is stopped.");
        }
    }

    /** {@inheritDoc} */
    @Override public Collection<String> metaInfo() {
        return Arrays.asList("Time, ms", "cpu usr", "cpu sys", "cpu idl", "cpu wai",
            "cpu hiq", "cpu siq", "dsk read", "dsk writ", "net recv", "net send", "paging in",
            "paging out", "system int", "system csw");
    }

    /** {@inheritDoc} */
    @Override public synchronized Collection<BenchmarkProbePoint> points() {
        Collection<BenchmarkProbePoint> ret = collected;

        collected = new ArrayList<>(ret.size() + 5);

        return ret;
    }

    /**
     * @param pnt Probe point.
     */
    private synchronized void collectPoint(BenchmarkProbePoint pnt) {
        collected.add(pnt);
    }

    /**
     * @param lineNum Line number.
     * @param line Line to parse.
     */
    private void parseLine(int lineNum, String line) {
        if (lineNum == 0) {
            Matcher m = FIRST_LINE.matcher(line);

            if (!m.matches())
                cfg.output().println("WARNING: dstat returned unexpected first line: '" + line + "'.");
        }
        else if (lineNum == 1) {
            Matcher m = HEADER_LINE.matcher(line);

            if (!m.matches())
                cfg.output().println("ERROR: Header line does not match expected header " +
                    "[exp=" + HEADER_LINE + ", act=" + line + "].");
        }
        else {
            Matcher m = DSTAT_PAT.matcher(line);

            if (m.matches()) {
                try {
                    BenchmarkProbePoint pnt = new BenchmarkProbePoint(System.currentTimeMillis(),
                        new float[] {
                            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                            Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)),
                            Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)),
                            parseMem(m.group(7)), parseMem(m.group(8)),
                            parseMem(m.group(9)), parseMem(m.group(10)),
                            Integer.parseInt(m.group(11)), Integer.parseInt(m.group(12)),
                            Integer.parseInt(m.group(13)), Integer.parseInt(m.group(14)),
                        });

                    collectPoint(pnt);
                }
                catch (NumberFormatException e) {
                    cfg.output().println("ERROR: Can't parse line: '" + line + "'.");
                }
            }
            else
                cfg.output().println("ERROR: Can't parse line: '" + line + "'.");
        }
    }

    /**
     * @param val Value.
     * @return Parsed value.
     */
    private static int parseMem(String val) {
        if (val.isEmpty())
            throw new NumberFormatException("Value is empty");

        if (val.length() == 1)
            return Integer.parseInt(val);

        char last = val.charAt(val.length() - 1);

        int multipier;

        if (last == 'B')
            multipier = 1;
        else if (last == 'k' || last == 'K')
            multipier = 1024;
        else if (last == 'm' || last == 'M')
            multipier = 1048576;
        else
            throw new NumberFormatException("Unknown " + last + " for value " + val);

        return Integer.parseInt(val.substring(0, val.length() - 1)) * multipier;
    }

    /**
     * @param cfg Config.
     * @return Path to dstat executable.
     */
    private static String path(BenchmarkConfiguration cfg) {
        String res = cfg.customProperties().get(PATH);

        return res == null || res.isEmpty() ? DEFAULT_PATH : res;
    }

    /**
     * @param cfg Config.
     * @return Options of dstat.
     */
    private static Collection<String> opts(BenchmarkConfiguration cfg) {
        String res = cfg.customProperties().get(OPTS);

        res = res == null || res.isEmpty() ? DEFAULT_OPTS : res;

        return Arrays.asList(res.split("\\s+"));
    }
}