package com.trixiether.dashchan.chan.refugedobrochan;

import chan.content.VichanChanMarkup;
import chan.text.CommentEditor;

public class RefugeDobrochanChanMarkup extends VichanChanMarkup {

    public RefugeDobrochanChanMarkup() {
        super();
        addTag("s", TAG_STRIKE);
    }

    @Override
    public CommentEditor obtainCommentEditor(String boardName) {
        CommentEditor commentEditor = new CommentEditor();
        commentEditor.addTag(TAG_BOLD, "**", "**", CommentEditor.FLAG_ONE_LINE);
        commentEditor.addTag(TAG_ITALIC, "*", "*", CommentEditor.FLAG_ONE_LINE);
        commentEditor.addTag(TAG_STRIKE, "~~", "~~", CommentEditor.FLAG_ONE_LINE);
        commentEditor.addTag(TAG_SPOILER, "%%", "%%", CommentEditor.FLAG_ONE_LINE);
        return commentEditor;
    }

    @Override
    public boolean isTagSupported(String boardName, int tag) {
        return ((SUPPORTED_TAGS | TAG_STRIKE) & tag) == tag;
    }

}
