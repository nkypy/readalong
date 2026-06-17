package com.jchshi.readalong;

import java.util.Arrays;
import java.util.List;

public final class AppConfig {
    public static final List<WebEntry> SITES = Arrays.asList(
        new WebEntry("Read Along", "https://readalong.google.com/")
    );

    public static final int DEFAULT_SITE_INDEX = 0;

    private AppConfig() {
    }
}
