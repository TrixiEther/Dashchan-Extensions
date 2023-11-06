package com.trixiether.dashchan.chan.archb4k;

import chan.content.FoolFuukaChanLocator;

public class ArchB4KChanLocator extends FoolFuukaChanLocator {
    public ArchB4KChanLocator() {
        addChanHost("arch.b4k.co");
        setHttpsMode(HttpsMode.HTTPS_ONLY);
    }
}
