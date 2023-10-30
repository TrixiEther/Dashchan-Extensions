package com.mishiranu.dashchan.chan.dollchan;

import android.graphics.Bitmap;
import android.net.Uri;

import chan.content.ApiException;
import chan.content.ChanLocator;
import chan.content.InvalidResponseException;
import chan.content.WakabaChanPerformer;
import chan.content.model.Board;
import chan.content.model.BoardCategory;
import chan.content.model.Post;
import chan.content.model.Posts;
import chan.http.CookieBuilder;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.http.UrlEncodedEntity;
import chan.util.CommonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DollchanChanPerformer extends WakabaChanPerformer {

	private static final int AUTH_FIELD_BOARD = 0;
	private static final int AUTH_FIELD_PASSCODE = 1;
	private static final int AUTH_FIELD_PASSWORD = 2;
	private static final String COOKIE_AUTHORIZATION = "AUTHORIZATION";
	private static final String COOKIE_PASSCODE = "PASSCODE";

	private String userPassword;

	private boolean checkPassword(String password) {
		if (password == null)
		{
			return false;
		}
		if (userPassword == null)
		{
			return false;
		}
		return userPassword.equals(password);
	}

	@Override
	protected List<Posts> parseThreads(String boardName, InputStream input) throws IOException, chan.text.ParseException {
		return new DollchanPostsParser(this, boardName).convertThreads(input);
	}

	@Override
	protected List<Post> parsePosts(String boardName, InputStream input) throws IOException, chan.text.ParseException {
		return new DollchanPostsParser(this, boardName).convertPosts(input);
	}

	private static final Pattern PATTERN_BOARD_LIST = Pattern.compile("<li><a href=\"https://dollchan.net/([a-z0-9]+)/\">/([a-z0-9]+)</a> ?&ndash;(.*?)\\.?</li>");
	private static final Board[] FALLBACK_BOARD_LIST = new Board[] {
		new Board("a", "Anime board"),
		new Board("btb", "Bytebeat music discussion board"),
		new Board("de", "Dollchan Extension discussion board"),
		new Board("test", "Atomboard engine test board"),
		new Board("ukr", "Board for Ukrainians")
	};

	@Override
	public ReadBoardsResult onReadBoards(ReadBoardsData data) {

		DollchanChanLocator locator = ChanLocator.get(this);

		Uri uri = locator.buildPath();
		String responseText;

		try {
			HttpResponse response = new HttpRequest(uri, data).perform();
			responseText = response.readString();
		} catch (HttpException e) {
			// cant get the list, so fallback
			return new ReadBoardsResult(new BoardCategory(null, FALLBACK_BOARD_LIST));
		}

		Matcher m = PATTERN_BOARD_LIST.matcher(responseText);

		ArrayList<Board> boards = new ArrayList<>();

		while (m.find())
		{
			String boardId = m.group(1);
			String boardDescription = m.group(3);
			boards.add(new Board(boardId, boardDescription));
		}

		if (boards.isEmpty())
		{
			// for some reason it's empty, so fallback
			return new ReadBoardsResult(new BoardCategory(null, FALLBACK_BOARD_LIST));
		}

		return new ReadBoardsResult(new BoardCategory(null, boards));
	}

	@Override
	public CheckAuthorizationResult onCheckAuthorization(CheckAuthorizationData data)
			throws HttpException {
		String boardName = data.authorizationData[AUTH_FIELD_BOARD];
		String passcode = data.authorizationData[AUTH_FIELD_PASSCODE];
		String password = data.authorizationData[AUTH_FIELD_PASSWORD];
		if (password != null && !password.equals("")) {
			return new CheckAuthorizationResult(authorizeUserForManage(data, boardName, password) != null);
		} else if (passcode != null && !passcode.equals("")) {
			return new CheckAuthorizationResult(authorizeUserForPasscode(data, boardName));
		} else {
			return new CheckAuthorizationResult(false);
		}
	}

	private String authorizeUserFromConfigurationForManage(HttpRequest.Preset preset, boolean force)
			throws HttpException {
		String[] authorizationData = DollchanChanConfiguration.get(this).getUserAuthorizationData();
		String boardName = authorizationData[AUTH_FIELD_BOARD];
		String password = authorizationData[AUTH_FIELD_PASSWORD];
		if (force || !checkPassword(password)) {
			if (password != null) {
				return authorizeUserForManage(preset, boardName, password);
			} else {
				userPassword = null;
			}
		}
		return userPassword;
	}

	private String authorizeUserForManage(HttpRequest.Preset preset, String boardName, String password)
			throws HttpException {
		userPassword = null;
		DollchanChanLocator locator = ChanLocator.get(this);

		Uri uri = locator.buildPath(boardName, "imgboard.php?manage");
		HttpResponse response = new HttpRequest(uri, preset).setPostMethod(
			new UrlEncodedEntity("managepassword", password)).perform();

		DollchanChanConfiguration configuration = DollchanChanConfiguration.get(this);

		configuration.storeCookie(COOKIE_AUTHORIZATION,
			response.getCookieValue("PHPSESSID"), "Authorization");

		String responseString = response.readString();
		if (responseString != null && responseString.contains("?manage&logout\">Log Out<")) {
			this.userPassword = password;
			return password;
		}

		userPassword = null;
		return null;
	}

	private boolean doAuthorizeUserForPasscode(HttpRequest.Preset preset, String boardName)
			throws HttpException {
		DollchanChanConfiguration configuration = DollchanChanConfiguration.get(this);
		DollchanChanLocator locator = ChanLocator.get(this);

		String[] authorizationData = DollchanChanConfiguration.get(this).getUserAuthorizationData();
		if (authorizationData[AUTH_FIELD_PASSCODE] == null) {
			configuration.storeCookie(COOKIE_PASSCODE, null, "Passcode");
			return false;
		}

		Uri uri = locator.buildPath(boardName, "imgboard.php?passcode");
		HttpResponse response = new HttpRequest(uri, preset).setPostMethod(
			new UrlEncodedEntity("passcode",
				authorizationData[AUTH_FIELD_PASSCODE])).perform();

		String responseString = response.readString();
		if (responseString == null || !responseString.contains("You have logged in.")) {
			configuration.storeCookie(COOKIE_PASSCODE, null, "Passcode");
			return false;
		}

		configuration.storeCookie(COOKIE_PASSCODE,
				response.getCookieValue("PHPSESSID"), "Passcode");

		return true;
	}

	private boolean authorizeUserForPasscode(HttpRequest.Preset preset, String boardName)
			throws HttpException {
		DollchanChanLocator locator = ChanLocator.get(this);

		Uri uri = locator.buildPath(boardName, "imgboard.php?passcode&check");
		HttpResponse checkResponse;

		HttpRequest request = new HttpRequest(uri, preset).addCookie(buildPasscodeCookies());

		try {
			checkResponse = request.perform();

			if (checkResponse.getResponseCode() == 200 && "OK".equals(checkResponse.readString())) {
				return true;
			}
		} catch (HttpException e) {
			if (e.getResponseCode() != 403) {
				// something else
				throw e;
			}
		}

		return doAuthorizeUserForPasscode(preset, boardName);
	}

	private static final Pattern PATTERN_POST_ERROR = Pattern.compile("<div class=\"reply\".*?>(.*?)</div>");
	private static final Pattern PATTERN_POST_ERROR_UNCOMMON = Pattern.compile("<h2>(.*?)</h2>");
	@Override
	public ReadCaptchaResult onReadCaptcha(ReadCaptchaData data) throws HttpException, InvalidResponseException {
		if (authorizeUserFromConfigurationForManage(data, false) != null) {
			CaptchaData captchaData = new CaptchaData();
			captchaData.put("manage", "1");
			return new ReadCaptchaResult(CaptchaState.SKIP, captchaData);
		}

		if (authorizeUserForPasscode(data, data.boardName)) {
			CaptchaData captchaData = new CaptchaData();
			captchaData.put("pass", "1");
			return new ReadCaptchaResult(CaptchaState.PASS, captchaData);
		}

		DollchanChanLocator locator = DollchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "inc", "captcha.php");
		HttpResponse response = new HttpRequest(uri, data).perform();
		Bitmap image = response.readBitmap();
		String captchaId = response.getCookieValue("PHPSESSID");
		if (image == null || captchaId == null) {
			throw new InvalidResponseException();
		}

		int[] pixels = new int[image.getWidth() * image.getHeight()];
		image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
		Bitmap newImage = Bitmap.createBitmap(pixels, image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
		image.recycle();
		image = CommonUtils.trimBitmap(newImage, 0x00000000);
		if (image == null) {
			image = newImage;
		} else if (image != newImage) {
			newImage.recycle();
		}

		CaptchaData captchaData = new CaptchaData();
		captchaData.put(CaptchaData.CHALLENGE, captchaId);
		return new ReadCaptchaResult(CaptchaState.CAPTCHA, captchaData).setImage(image);
	}

	private CookieBuilder buildCookies(CaptchaData data) {
		if (data != null) {
			if (data.get("pass") != null) {
				return buildPasscodeCookies();
			}
			if (data.get("manage") != null) {
				return buildCookiesWithAuthorizationPass();
			}
		}
		CookieBuilder builder = new CookieBuilder();
		if (data != null) {
			builder.append("PHPSESSID", data.get(CaptchaData.CHALLENGE));
		}
		return builder;
	}

	private CookieBuilder buildPasscodeCookies() {
		DollchanChanConfiguration configuration = DollchanChanConfiguration.get(this);
		CookieBuilder builder = new CookieBuilder();
		String passcodeCookie = configuration.getCookie(COOKIE_PASSCODE);
		if (passcodeCookie != null)
		{
			builder.append("PHPSESSID", passcodeCookie);
		}
		return builder;
	}

	private SendPostResult doSendPost(SendPostData data, boolean forceAuth)
			throws HttpException, ApiException, InvalidResponseException {

		MultipartEntity entity = new MultipartEntity();

		entity.add("parent", data.threadNumber != null ? data.threadNumber : "0");
		entity.add("name", data.name != null ? data.name : "");
		entity.add("subject", data.subject != null ? data.subject : "");
		entity.add("message", data.comment);
		entity.add("password", data.password);
		if (data.optionSage) {
			entity.add("email", "sage");
		} else {
			entity.add("email", data.email != null ? data.email : "");
		}
		if (data.attachments != null) {
			for (int i = 0; i < data.attachments.length; i++) {
				SendPostData.Attachment attachment = data.attachments[i];
				attachment.addToEntity(entity, "file[]");
			}
		}

		DollchanChanLocator locator = ChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php");

		if (data.captchaData != null && data.captchaData.get(CaptchaData.INPUT) != null) {
			entity.add("captcha", data.captchaData.get(CaptchaData.INPUT));
		}

		HttpResponse response = new HttpRequest(uri, data).setPostMethod(entity).
			addCookie(buildCookies(data.captchaData)).
			setRedirectHandler(HttpRequest.RedirectHandler.NONE).perform();

		String responseText;
		if (response.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
			uri = response.getRedirectedUri();
			String path = uri.getPath();
			if (path == null) {
				throw new InvalidResponseException();
			}
			if (!path.startsWith("/error")) {
				String threadNumber = locator.getThreadNumber(uri);
				if (threadNumber == null) {
					throw new InvalidResponseException();
				}
				return new SendPostResult(threadNumber, null);
			}
			responseText = new HttpRequest(uri, data).addCookie(buildCookies(data.captchaData)).perform().readString();
		} else {
			responseText = response.readString();
		}

		String errorMessage = null;
		Matcher matcher = PATTERN_POST_ERROR.matcher(responseText);
		if (matcher.find()) {
			errorMessage = matcher.group(1);
		} else {
			matcher = PATTERN_POST_ERROR_UNCOMMON.matcher(responseText);
			if (matcher.find()) {
				errorMessage = matcher.group(1);
			}
		}
		if (errorMessage != null) {
			int errorType = 0;
			if (errorMessage.contains("CAPTCHA")) {
				if (!forceAuth) {
					// a cookie was expired, so we need to enter captcha
					// check if we can re-login and try again
					if (authorizeUserFromConfigurationForManage(data, true) != null) {
						return doSendPost(data, true);
					} else if (doAuthorizeUserForPasscode(data, data.boardName)) {
						return doSendPost(data, true);
					}
				}
				errorType = ApiException.SEND_ERROR_CAPTCHA;
			}
			if (errorType != 0) {
				throw new ApiException(errorType);
			} else {
				CommonUtils.writeLog("Dollchan send message", errorMessage);
				throw new ApiException(errorMessage);
			}
		}
		return null;
	}

	@Override
	public SendPostResult onSendPost(SendPostData data)
			throws HttpException, ApiException, InvalidResponseException {
		return doSendPost(data, false);
	}

	protected static Pattern POSTER_IP = Pattern.compile("<input type=\"hidden\" name=\"bans\" value=\"(.*?)\">");
	protected static Pattern REASON_PARSER = Pattern.compile("([0-9]+)/?([0-9]+)? (.*+)");

	private CookieBuilder buildCookiesWithAuthorizationPass() {
		CookieBuilder builder = new CookieBuilder();
		String auth = DollchanChanConfiguration.get(this).getCookie(COOKIE_AUTHORIZATION);
		if (auth != null)
		{
			builder.append("PHPSESSID", auth);
		}
		return builder;
	}

	private SendReportPostsResult doSendReportPosts(SendReportPostsData data, boolean reAuth)
		throws HttpException, ApiException
	{
		if (authorizeUserFromConfigurationForManage(data, reAuth) == null)
		{
			throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
		}

		if (DollchanChanConfiguration.REPORDING_DELETE.equals(data.type))
		{
			for (String postNumber : data.postNumbers) {
				DollchanChanLocator locator = DollchanChanLocator.get(this);

				Uri uri = locator.buildPath(data.boardName,
					"imgboard.php?manage&delete=" + postNumber);
				String response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();
				if (response.contains("Enter an administrator or moderator password")) {
					if (!reAuth) {
						// re-authenticate and try again
						return doSendReportPosts(data, true);
					}
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		if (DollchanChanConfiguration.REPORDING_BAN.equals(data.type) ||
			DollchanChanConfiguration.REPORDING_WARNING.equals(data.type))
		{
			for (String postNumber : data.postNumbers)
			{
				DollchanChanLocator locator = DollchanChanLocator.get(this);
				Uri uri = locator.buildPath(data.boardName,
					"imgboard.php?manage=&moderate=" + postNumber);
				String response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();

				Matcher m = POSTER_IP.matcher(response);
				if (!m.find())
				{
					throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
				}

				String ip = m.group(1);

				uri = locator.buildPath(data.boardName,
						"imgboard.php?manage&bans");

				int days;
				int subnet = 0;
				String reason;

				if (DollchanChanConfiguration.REPORDING_WARNING.equals(data.type))
				{
					// one second-ban means a warning
					days = 1;
					reason = data.comment;
				}
				else
				{
					Matcher mReason = REASON_PARSER.matcher(data.comment);

					if (!mReason.find())
					{
						throw new ApiException("Comment must be: <number of days> <reason>");
					}

					try
					{
						days = Integer.parseInt(mReason.group(1));
					}
					catch (NumberFormatException e)
					{
						throw new ApiException("Comment must be: <number of days> <reason>");
					}

					if (mReason.group(2) != null)
					{
						try
						{
							subnet = Integer.parseInt(mReason.group(2));
						}
						catch (NumberFormatException ignored) {}
					}
					reason = mReason.group(3);
					days *= 86400;
				}

				if (subnet != 0)
				{
					ip = ip + "/" + subnet;
				}

				MultipartEntity entity = new MultipartEntity(
					"ip", ip,
					"expire", Integer.toString(days),
					"reason", reason
				);

				response = new HttpRequest(uri, data).
					addCookie(buildCookiesWithAuthorizationPass()).setPostMethod(entity).
						perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					if (!reAuth) {
						// re-authenticate and try again
						return doSendReportPosts(data, true);
					}
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		if (DollchanChanConfiguration.REPORDING_BAN_DELETE_ALL.equals(data.type))
		{
			for (String postNumber : data.postNumbers) {
				DollchanChanLocator locator = DollchanChanLocator.get(this);
				Uri uri = locator.buildPath(data.boardName,
						"imgboard.php?manage=&moderate=" + postNumber);
				String response = new HttpRequest(uri, data).
						addCookie(buildCookiesWithAuthorizationPass()).
						perform().readString();

				Matcher m = POSTER_IP.matcher(response);
				if (!m.find())
				{
					throw new ApiException(ApiException.DELETE_ERROR_NOT_FOUND);
				}

				String ip = m.group(1);
				String originalIP = ip;

				Matcher reason = REASON_PARSER.matcher(data.comment);
				if (!reason.find())
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				int days;

				try
				{
					days = Integer.parseInt(reason.group(1));
				}
				catch (NumberFormatException e)
				{
					throw new ApiException("Comment must be: <number of days> <reason>");
				}

				int subnet = 0;

				if (reason.group(2) != null)
				{
					try
					{
						subnet = Integer.parseInt(reason.group(2));
					}
					catch (NumberFormatException ignored) {}
				}

				if (subnet != 0)
				{
					ip += "/" + subnet;
				}

				MultipartEntity entity = new MultipartEntity(
					"ip", ip,
					"expire", Integer.toString(days * 86400),
					"reason", reason.group(3)
				);

				response = new HttpRequest(
						locator.buildPath(data.boardName, "imgboard.php?manage&bans"),
					data).addCookie(buildCookiesWithAuthorizationPass()).setPostMethod(entity).
					perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					if (!reAuth) {
						// re-authenticate and try again
						return doSendReportPosts(data, true);
					}
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}

				response = new HttpRequest(
						locator.buildPath(data.boardName, "imgboard.php?manage&delall=" + originalIP),
					data).addCookie(buildCookiesWithAuthorizationPass()).
					perform().readString();

				if (response.contains("Enter an administrator or moderator password")) {
					if (!reAuth) {
						// re-authenticate and try again
						return doSendReportPosts(data, true);
					}
					throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
				}
			}

			return new SendReportPostsResult();
		}

		throw new ApiException(ApiException.SEND_ERROR_NO_ACCESS);
	}

	@Override
	public SendReportPostsResult onSendReportPosts(SendReportPostsData data)
			throws HttpException, ApiException
	{
		return doSendReportPosts(data, false);
	}

	@Override
	public SendDeletePostsResult onSendDeletePosts(SendDeletePostsData data) throws HttpException, ApiException {
		UrlEncodedEntity entity = new UrlEncodedEntity(
			"password", data.password
		);

		for (String postNumber : data.postNumbers) {
			entity.add("delete", postNumber);
		}

		DollchanChanLocator locator = DollchanChanLocator.get(this);
		Uri uri = locator.buildPath(data.boardName, "imgboard.php?delete");
		String response = new HttpRequest(uri, data).setPostMethod(entity)
			.setRedirectHandler(HttpRequest.RedirectHandler.STRICT).perform().readString();
		if (response.contains("Invalid password")) {
			throw new ApiException(ApiException.DELETE_ERROR_PASSWORD);
		}
		return new SendDeletePostsResult();
	}
}
