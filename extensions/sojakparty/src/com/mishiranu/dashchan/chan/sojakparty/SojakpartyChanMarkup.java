package com.mishiranu.dashchan.chan.sojakparty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.util.Pair;

import chan.content.ChanMarkup;
import chan.text.CommentEditor;

public class SojakpartyChanMarkup extends ChanMarkup {
	private static final int SUPPORTED_TAGS = TAG_BOLD | TAG_ITALIC | TAG_UNDERLINE | TAG_STRIKE | TAG_CODE | TAG_SPOILER | TAG_QUOTE;

	public SojakpartyChanMarkup() {
		addTag("strong", TAG_BOLD);
		addTag("em", TAG_ITALIC);
		addTag("u", TAG_UNDERLINE);
		addTag("s", TAG_STRIKE);
		addTag("code", TAG_CODE);
		addTag("span", "spoiler", TAG_SPOILER);
		addTag("span", "quote", TAG_QUOTE);
		addColorable("div");
		addColorable("span");
	}

	@Override
	public CommentEditor obtainCommentEditor(String boardName) {
		CommentEditor commentEditor = new CommentEditor();
		commentEditor.addTag(TAG_BOLD, "'''", "'''", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_ITALIC, "''", "''", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_UNDERLINE, "__", "__", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_STRIKE, "~~", "~~", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_CODE, "```", "```", CommentEditor.FLAG_ONE_LINE);
		commentEditor.addTag(TAG_SPOILER, "**", "**", CommentEditor.FLAG_ONE_LINE);
		return commentEditor;
	}

	@Override
	public boolean isTagSupported(String boardName, int tag) {
		return (SUPPORTED_TAGS & tag) == tag;
	}

	private static final Pattern THREAD_LINK = Pattern.compile("(\\d+).*\\.html(?:#(\\d+))?$");

	@Override
	public Pair<String, String> obtainPostLinkThreadPostNumbers(String uriString) {
		Matcher matcher = THREAD_LINK.matcher(uriString);
		if (matcher.find()) {
			return new Pair<>(matcher.group(1), matcher.group(2));
		}
		return null;
	}
}