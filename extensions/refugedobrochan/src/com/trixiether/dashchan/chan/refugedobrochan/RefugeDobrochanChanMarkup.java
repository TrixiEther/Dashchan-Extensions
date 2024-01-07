package com.trixiether.dashchan.chan.refugedobrochan;

import chan.content.VichanChanMarkup;

public class RefugeDobrochanChanMarkup extends VichanChanMarkup {

    public RefugeDobrochanChanMarkup() {
        super();
        addTag("s", TAG_STRIKE);
    }

    @Override
    public boolean isTagSupported(String boardName, int tag) {
        return ((SUPPORTED_TAGS | TAG_STRIKE) & tag) == tag;
    }

}
