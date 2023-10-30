package com.mishiranu.dashchan.chan.dollchan;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.WakabaChanConfiguration;
import chan.content.WakabaChanLocator;
import chan.content.WakabaPostsParser;
import chan.content.model.Attachment;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.StringUtils;

public class DollchanPostsParser {
	private boolean reflinkParsing = false;

	private static final SimpleDateFormat DATE_FORMAT;

	static {
		DATE_FORMAT = new SimpleDateFormat("dd.MM.yy EEE HH:mm:ss", Locale.UK);
		DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
	}

	protected final DollchanChanConfiguration configuration;
	protected final DollchanChanLocator locator;
	protected final String boardName;

	protected String parent;
	protected Posts thread;
	protected Post post;
	protected ArrayList<FileAttachment> attachments = null;
	protected FileAttachment attachment;
	protected ArrayList<Posts> threads;
	protected final ArrayList<Post> posts = new ArrayList<>();
	protected int maxNumberOfPages = 0;

	protected boolean headerHandling = false;
	protected boolean originalNameFromLink;

	private static final Pattern FILE_SIZE = Pattern.compile("([\\d.]+) (\\w+), (\\d+)x(\\d+)(?:, (.+))?");
	private static final Pattern NUMBER = Pattern.compile("\\d+");

	public DollchanPostsParser(Object linked, String boardName) {
		originalNameFromLink = true;
		this.configuration = DollchanChanConfiguration.get(linked);
		this.locator = DollchanChanLocator.get(linked);
		this.boardName = boardName;
	}

	protected void parseThis(TemplateParser<DollchanPostsParser> parser, InputStream input)
			throws IOException, ParseException {
		parser.parse(new InputStreamReader(input), this);
	}

	private void closeThread() {
		if (thread != null) {
			thread.setPosts(posts);
			thread.addPostsCount(posts.size());
			threads.add(thread);
			posts.clear();
		}
	}

	private static final TemplateParser<DollchanPostsParser> PARSER =
		TemplateParser.<DollchanPostsParser>builder()
		.equals("input", "name", "delete")
		.open((instance, holder, tagName, attributes) -> {
			if ("checkbox".equals(attributes.get("type"))) {
				holder.headerHandling = true;
				if (holder.post == null || holder.post.getPostNumber() == null) {
					String number = attributes.get("value");
					if (holder.post == null) {
						holder.post = new Post();
					}
					holder.post.setPostNumber(number);
					holder.parent = number;
					if (holder.threads != null) {
						holder.closeThread();
						holder.thread = new Posts();
						holder.attachments = null;
					}
				}
			}
			return false;
		})
		.starts("td", "id", "reply")
		.open((instance, holder, tagName, attributes) -> {
			String number = StringUtils.emptyIfNull(attributes.get("id")).substring(5);
			Post post = new Post();
			post.setParentPostNumber(holder.parent);
			post.setPostNumber(number);
			holder.post = post;
			holder.attachments = null;
			return false;
		})
		.equals("span", "class", "filesize")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.post == null) {
				holder.post = new Post();
			}
			if (holder.attachments == null) {
				holder.attachments = new ArrayList<>();
			}
			if (holder.attachment != null) {
				holder.attachments.add(holder.attachment);
			}
			holder.attachment = new FileAttachment();
			return false;
		})
		.name("a")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachment != null && holder.attachment.getFileUri(holder.locator) == null) {
				holder.attachment.setFileUri(holder.locator, holder.locator.buildPath(attributes.get("href")));

				String size = attributes.get("data-size");
				if (size != null)
				{
					try
					{
						int sz = Integer.parseInt(size);
						holder.attachment.setSize(sz);
					}
					catch (NumberFormatException ignored) {}
				}

				String width = attributes.get("data-width");
				if (width != null)
				{
					try
					{
						int w = Integer.parseInt(width);
						holder.attachment.setWidth(w);
					}
					catch (NumberFormatException ignored) {}
				}

				String height = attributes.get("data-height");
				if (height != null)
				{
					try
					{
						int h = Integer.parseInt(height);
						holder.attachment.setHeight(h);
					}
					catch (NumberFormatException ignored) {}
				}

				return holder.originalNameFromLink;
			}
			return false;
		})
		.content((instance, holder, text) -> holder.attachment
				.setOriginalName(StringUtils.clearHtml(text).trim()))
		.starts("img", "class", "thumb")
		.open((instance, holder, tagName, attributes) -> {
			String src = attributes.get("src");
			if (src != null) {
				if (src.contains("/thumb/")) {
					holder.attachment.setThumbnailUri(holder.locator, holder.locator.buildPath(src));
				}
				if (src.contains("extras/icons/spoiler.png")) {
					holder.attachment.setSpoiler(true);
				}
			}
			if (holder.attachments == null) {
				holder.attachments = new ArrayList<>();
			}
			holder.attachments.add(holder.attachment);
			holder.post.setAttachments(holder.attachments);
			holder.attachment = null;
			return false;
		})
		.starts("video", "class", "thumb")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachments == null) {
				holder.attachments = new ArrayList<>();
			}
			holder.attachments.add(holder.attachment);
			holder.post.setAttachments(holder.attachments);
			holder.attachment = null;
			return false;
		})
		.equals("div", "class", "nothumb")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachment.getSize() > 0 || holder.attachment.getWidth() > 0 ||
					holder.attachment.getHeight() > 0) {
				if (holder.attachments == null) {
					holder.attachments = new ArrayList<>();
				}
				holder.attachments.add(holder.attachment);
				holder.post.setAttachments(holder.attachments);
			}
			holder.attachment = null;
			return false;
		})
		.equals("span", "class", "filetitle")
		.equals("span", "class", "replytitle")
		.content((instance, holder, text) -> holder.post
				.setSubject(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())))
		.equals("img", "class", "poster-country")
		.open((instance, holder, tagName, attributes) -> {
			String title = attributes.get("title");
			String src = attributes.get("src");
			if (title != null && src != null) {
				Uri fullSrc = holder.locator.buildPath(src);
				holder.post.setIcons(new Icon(holder.locator, fullSrc, title));
			}
			return false;
		})
		.equals("span", "class", "posteruid")
		.content((instance, holder, text) -> {
			String id = StringUtils.clearHtml(text);
			holder.post.setIdentifier(id);
		})
		.starts("span", "class", "postername")
		.content((instance, holder, text) -> {
			if (holder.post.getName() != null) {
				holder.post.setName(holder.post.getName() + " " + text);
			} else {
				holder.post.setName(text);
			}
		})
		.equals("span", "class", "postername postername-admin")
		.content((instance, holder, text) -> {
			holder.post.setCapcode(StringUtils.clearHtml(text));
		})
		.equals("span", "class", "postertrip")
		.content((instance, holder, text) -> holder.post
				.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())))
		.equals("span", "class", "posterdate")
		.open((instance, holder, tagName, attributes) -> {
			String timestamp = attributes.get("data-timestamp");
			if (timestamp != null)
			{
				try {
					holder.post.setTimestamp(Long.parseLong(timestamp) * 1000);
				} catch (NumberFormatException ignored) {
					// Ignore exception
				}
			}
			return true;
		})
		.equals("div", "class", "message")
		.content((instance, holder, text) -> {
			text = text.trim();
			int index = text.lastIndexOf("<div class=\"abbrev\">");
			if (index >= 0) {
				text = text.substring(0, index).trim();
			}
			holder.post.setComment(text);
			holder.posts.add(holder.post);
			holder.post = null;
		})
		.equals("div", "class", "omittedposts")
		.content((instance, holder, text) -> {
			if (holder.threads != null) {
				Matcher matcher = NUMBER.matcher(text);
				if (matcher.find()) {
					holder.thread.addPostsCount(Integer.parseInt(matcher.group()));
				}
			}
		})
		.equals("div", "class", "logo")
		.content((instance, holder, text) -> holder.storeBoardTitle(StringUtils.clearHtml(text).trim()))
		.ends("a", "href", ".html")
		.content((instance, holder, text) -> {
			if (text.matches("\\\\d+"))
			{
				try {
					int pagesCount = Integer.parseInt(text) + 1;
					if (holder.maxNumberOfPages < pagesCount)
					{
						holder.maxNumberOfPages = pagesCount;
						holder.configuration.storePagesCount(holder.boardName, pagesCount);
					}
				} catch (NumberFormatException e) {
					// Ignore exception
				}
			}
		})
		.name("label")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.post != null) {
				holder.headerHandling = true;
			}
			return false;
		})
		.equals("span", "class", "reflink")
		.open((instance, holder, tagName, attributes) -> {
			holder.reflinkParsing = true;
			return false;
		})
		.name("a")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.reflinkParsing) {
				holder.reflinkParsing = false;
				if (holder.post != null && holder.post.getParentPostNumber() == null) {
					Uri uri = Uri.parse(attributes.get("href"));
					String threadNumber = holder.locator.getThreadNumber(uri);
					if (threadNumber != null && !threadNumber.equals(holder.post.getPostNumber())) {
						holder.post.setParentPostNumber(threadNumber);
					}
				}
			}
			return false;
		})
		.ends("img", "src", "/icons/sticky.png")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.post != null) {
				holder.post.setSticky(true);
			}
			return false;
		})
		.ends("img", "src", "/icons/endless.png")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.post != null) {
				holder.post.setCyclical(true);
			}
			return false;
		})
		.prepare();


	public ArrayList<Posts> convertThreads(InputStream input) throws IOException, ParseException {
		threads = new ArrayList<>();
		parseThis(PARSER, input);
		closeThread();
		if (threads.size() > 0) {
			updateConfiguration();
			return threads;
		}
		return null;
	}

	public ArrayList<Post> convertPosts(InputStream input) throws IOException, ParseException {
		parseThis(PARSER, input);
		if (posts.size() > 0) {
			updateConfiguration();
			return posts;
		}
		return null;
	}

	protected void updateConfiguration() {}

	protected void setNameEmail(String nameHtml, String email) {
		if (email != null) {
			if (email.toLowerCase(Locale.US).contains("sage")) {
				post.setSage(true);
			} else {
				post.setEmail(email);
			}
		}
		post.setName(StringUtils.nullIfEmpty(StringUtils.clearHtml(nameHtml).trim()));
	}

	protected void storeBoardTitle(String title) {
		if (!StringUtils.isEmpty(title)) {
			configuration.storeBoardTitle(boardName, title);
		}
	}
}
