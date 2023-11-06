package com.trixiether.dashchan.chan.archb4k;

import chan.content.FoolFuukaChanPerformer;
import chan.content.model.PostsParser;

public class ArchB4KChanPerformer extends FoolFuukaChanPerformer {
    @Override
    protected PostsParser getPostsParser() {
        return new ArchB4KPostsParser(this);
    }
}
