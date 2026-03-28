package com.mishiranu.dashchan.chan.dollchan;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.model.FileAttachment;
import chan.content.model.Icon;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.text.ParseException;
import chan.text.TemplateParser;
import chan.util.CommonUtils;
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
	protected ArrayList<Icon> icons;
	protected final ArrayList<Post> posts = new ArrayList<>();
	protected int maxNumberOfPages = 0;

	protected boolean headerHandling = false;
	protected boolean originalNameFromLink;

	private static final Pattern NUMBER = Pattern.compile("\\d+");
	private static final Pattern FILE_SIZE = Pattern.compile("\\(([^,]+),\\s*(\\d+)x(\\d+)\\)");

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

	private static long parseSizeToBytes(String sizeStr) {
		// Examples: "220.93KB", "0.96MB", "273.64KB"
		sizeStr = sizeStr.trim().toUpperCase(Locale.US);
		try {
			int unitIndex = sizeStr.length();
			while (unitIndex > 0 && Character.isLetter(sizeStr.charAt(unitIndex - 1))) {
				unitIndex--;
			}
			if (unitIndex <= 0) return 0L;
			double value = Double.parseDouble(sizeStr.substring(0, unitIndex));
			String unit = sizeStr.substring(unitIndex);
			long multiplier;
			if (unit.equals("KB")) multiplier = 1024L;
			else if (unit.equals("MB")) multiplier = 1024L * 1024L;
			else if (unit.equals("B")) multiplier = 1L;
			else multiplier = 1L; // Fallback
			return (long) (value * multiplier);
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	private static final TemplateParser<DollchanPostsParser> PARSER =
		TemplateParser.<DollchanPostsParser>builder()
		.starts("section", "id", "thread")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.threads != null) {
				holder.closeThread();
				holder.thread = new Posts();
				holder.attachments = null;
			}

			holder.parent = StringUtils.emptyIfNull(attributes.get("id")).substring(6);
			return false;
		})
		.starts("article", "id", "post")
		.open((instance, holder, tagName, attributes) -> {
			holder.headerHandling = true;
			String number = StringUtils.emptyIfNull(attributes.get("id")).substring(4);
			holder.icons = null;
			Post post = new Post();
			post.setParentPostNumber(holder.parent);
			post.setPostNumber(number);
			holder.post = post;
			holder.attachments = null;
			return false;
		})
		// Atomboard: start new attachment for each post-file block
		.equals("figure", "class", "post-file")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.post == null) {
				holder.post = new Post();
				holder.icons = null;
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
		// Full file link (name only, URL from file-wrap)
		.equals("a", "class", "file-fullname")
		.open((instance, holder, tagName, attributes) -> {
			// In Atomboard markup full image URL is inside file-wrap's <a>, not here
			return false;
		})
		// File-info: size and dimensions, plus name pieces inside
		.equals("figcaption", "class", "file-info")
		.content((instance, holder, text) -> {
			if (holder.attachment != null) {
				// Strip HTML entities/spans, keep readable text
				String plain = StringUtils.clearHtml(text);
				Matcher m = FILE_SIZE.matcher(plain);
				if (m.find()) {
					String sizeStr = m.group(1); // e.g. "220.93KB" or "0.96MB"
					String widthStr = m.group(2);
					String heightStr = m.group(3);
					long bytes = parseSizeToBytes(sizeStr);
					if (bytes > 0) holder.attachment.setSize((int)bytes);
					try {
						int w = Integer.parseInt(widthStr);
						int h = Integer.parseInt(heightStr);
						holder.attachment.setWidth(w);
						holder.attachment.setHeight(h);
					} catch (NumberFormatException ignored) {}
				}
			}
		})
		// Original name parts
		.equals("span", "class", "file-name")
		.content((instance, holder, text) -> {
			if (holder.attachment != null) {
				String base = StringUtils.clearHtml(text).trim();
				holder.attachment.setOriginalName(base);
			}
		})
		.equals("span", "class", "file-extension")
		.content((instance, holder, text) -> {
			if (holder.attachment != null) {
				String ext = StringUtils.clearHtml(text).trim();
				String orig = holder.attachment.getOriginalName();
				if (orig == null) orig = "";
				holder.attachment.setOriginalName(orig + "." + ext);
			}
		})
		// Dimensions from file-wrap
		.equals("div", "class", "file-wrap")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachment != null) {
				String w = attributes.get("data-width");
				String h = attributes.get("data-height");
				try {
					if (w != null) holder.attachment.setWidth(Integer.parseInt(w));
					if (h != null) holder.attachment.setHeight(Integer.parseInt(h));
				} catch (NumberFormatException ignored) {}
			}
			return false;
		})
		// Full image link inside file-wrap
		.equals("a", "target", "_blank")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachment != null && holder.attachment.getFileUri(holder.locator) == null) {
				String href = attributes.get("href");
				if (href != null) {
					holder.attachment.setFileUri(holder.locator, holder.locator.buildPath(href));
				}
			}
			return false;
		})
		// Thumbnail
		.starts("img", "class", "file-thumb")
		.open((instance, holder, tagName, attributes) -> {
			String src = attributes.get("src");
			if (src != null && holder.attachment != null) {
				if (src.contains("/thumb/")) {
					holder.attachment.setThumbnailUri(holder.locator, holder.locator.buildPath(src));
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
		// Backward-compat for old markup (if any)
		.equals("div", "class", "nothumb")
		.open((instance, holder, tagName, attributes) -> {
			if (holder.attachment != null && (holder.attachment.getSize() > 0 ||
					holder.attachment.getWidth() > 0 || holder.attachment.getHeight() > 0)) {
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
				if (holder.icons == null) {
					holder.icons = new ArrayList<>();
				}
				Uri fullSrc = holder.locator.buildPath(src);
				holder.icons.add(new Icon(holder.locator, fullSrc, title));
				holder.post.setIcons(holder.icons);
			}
			return false;
		})
		.equals("img", "class", "poster-achievement")
		.open((instance, holder, tagName, attributes) -> {
			String title = attributes.get("title");
			String src = attributes.get("src");
			if (title != null && src != null) {
				if (holder.icons == null) {
					holder.icons = new ArrayList<>();
				}
				Uri fullSrc = holder.locator.buildPath(src);
				holder.icons.add(new Icon(holder.locator, fullSrc, title));
				holder.post.setIcons(holder.icons);
			}
			return false;
		})
		.equals("span", "class", "poster-uid")
		.content((instance, holder, text) -> {
			String id = StringUtils.clearHtml(text);
			holder.post.setIdentifier(id);
		})
		.starts("span", "class", "poster-name")
		.content((instance, holder, text) -> {
			if (holder.post.getName() != null) {
				holder.post.setName(holder.post.getName() + " " + text);
			} else {
				holder.post.setName(text);
			}
		})
		.equals("span", "class", "poster-name poster-name-admin")
		.content((instance, holder, text) -> {
			holder.post.setCapcode(StringUtils.clearHtml(text));
		})
		.equals("span", "class", "poster-trip")
		.content((instance, holder, text) -> holder.post
				.setTripcode(StringUtils.nullIfEmpty(StringUtils.clearHtml(text).trim())))
		.equals("time", "class", "post-date")
		.open((instance, holder, tagName, attributes) -> {
			String timestamp = attributes.get("datetime");
			if (timestamp != null)
			{
				try {
					long t = IsoTimestampParser.parseToMillis(timestamp);
					holder.post.setTimestamp(t);
				} catch (java.text.ParseException ignored) {
					// Ignore exception
				}
			}
			return true;
		})
		.equals("blockquote", "class", "post-message")
		.content((instance, holder, text) -> {
			text = text.trim();
			int index = text.lastIndexOf("<div class=\"abbrev\">");
			if (index >= 0) {
				text = text.substring(0, index).trim();
			}
			holder.post.setComment(text);
			holder.posts.add(holder.post);
			holder.post = null;
			holder.icons = null;
		})
		.equals("div", "class", "omittedposts")
		.content((instance, holder, text) -> {
			if (holder.threads != null && holder.thread != null) {
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
		// old-style reflink container
		.equals("span", "class", "reflink")
		.open((instance, holder, tagName, attributes) -> {
			holder.reflinkParsing = true;
			return false;
		})
		// new-style reflink container: post-id
		.equals("span", "class", "post-id")
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
