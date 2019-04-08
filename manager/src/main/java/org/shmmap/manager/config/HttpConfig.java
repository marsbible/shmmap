package org.shmmap.manager.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpConfig {
    private String ip = "0.0.0.0";
    private int port;
    private String post_limit;
    private int max_workers = 20;

    private long postLimit = -1;

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }


    public long getPostLimit() {
        return postLimit;
    }

    public void setPost_limit(String post_limit) {
        Pattern p = Pattern.compile("^([0-9]+)([KkBbMm])?$");
        Matcher m = p.matcher(post_limit);
        long x;

        this.post_limit = post_limit;
        if(m.matches()) {
            return;
        }

        if(m.groupCount() == 2) {
            x = Long.valueOf(m.group(1));

            switch(m.group(2).charAt(0)) {
                case 'K':
                case 'k':
                    x = x * 1024;
                    break;
                case 'B':
                case 'b':
                    x = x;
                    break;
                case 'M':
                case 'm':
                    x = x*1024*1024;
                    break;
                default:
                    break;
            }
        }
        else {
            x = Long.valueOf(m.group(1));
        }

        this.postLimit = x;
    }

    public int getMax_workers() {
        return max_workers;
    }

    public void setMax_workers(int max_workers) {
        this.max_workers = max_workers;
    }
}
