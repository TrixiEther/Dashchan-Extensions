package com.mishiranu.dashchan.chan.dollchan;

import android.content.res.Resources;
import android.util.Pair;
import chan.content.ChanConfiguration;
import chan.util.CommonUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class DollchanChanConfiguration extends ChanConfiguration {
	private static final String KEY_NAMES_ENABLED = "names_enabled";
	private static final String KEY_FLAGS_ENABLED = "flags_enabled";
	private static final String KEY_DELETE_ENABLED = "delete_enabled";
	private static final String KEY_CODE_ENABLED = "code_enabled";
	private static final String CAPTCHA_TYPE = "dollchan";

	public static final String REPORDING_REPORT = "report";
	public static final String REPORDING_DELETE = "delete";
	public static final String REPORDING_BAN = "ban";
	public static final String REPORDING_BAN_DELETE_ALL = "ban_delete_all";
	public static final String REPORDING_WARNING = "warning";

	public DollchanChanConfiguration() {
		setDefaultName("Anonymous");
		addCaptchaType(CAPTCHA_TYPE);
		request(OPTION_ALLOW_USER_AUTHORIZATION);
	}

	@Override
	public Board obtainBoardConfiguration(String boardName) {
		Board board = new Board();
		board.allowCatalog = true;
		board.allowPosting = true;
		board.allowDeleting = true;
		board.allowReporting = true;
		return board;
	}

	@Override
	public Captcha obtainCustomCaptchaConfiguration(String captchaType) {
		if (CAPTCHA_TYPE.equals(captchaType))
		{
			Captcha captcha = new Captcha();
			captcha.title = "Dollchan";
			captcha.input = Captcha.Input.LATIN;
			captcha.validity = Captcha.Validity.IN_THREAD;
			return captcha;
		}

		return null;
	}

	@Override
	public Reporting obtainReportingConfiguration(String boardName) {
		Reporting reporting = new Reporting();
		reporting.comment = true;
		reporting.multiplePosts = true;
		reporting.types.add(new Pair<>(REPORDING_REPORT, "Report"));
		reporting.types.add(new Pair<>(REPORDING_DELETE, "Delete Post [MOD]"));
		reporting.types.add(new Pair<>(REPORDING_BAN, "Ban User [MOD]"));
		reporting.types.add(new Pair<>(REPORDING_BAN_DELETE_ALL, "Ban & Delete All [MOD]"));
		reporting.types.add(new Pair<>(REPORDING_WARNING, "Issue a Warning [MOD]"));
		return reporting;
	}

	@Override
	public Authorization obtainUserAuthorizationConfiguration() {
		Authorization authorization = new Authorization();
		authorization.fieldsCount = 3;
		authorization.hints = new String[3];
		authorization.hints[0] = "Board (ukr, de, etc)";
		authorization.hints[1] = "Passcode, or";
		authorization.hints[2] = "Password";
		return authorization;
	}

	@Override
	public Posting obtainPostingConfiguration(String boardName, boolean newThread) {
		Posting posting = new Posting();
		posting.allowName = posting.allowTripcode = get(boardName, KEY_NAMES_ENABLED, true);
		posting.allowEmail = true;
		posting.allowSubject = true;
		posting.optionSage = true;
		posting.attachmentCount = 4;
		posting.attachmentMimeTypes.add("image/*");
		posting.attachmentMimeTypes.add("video/*");
		posting.attachmentMimeTypes.add("audio/*");
		posting.attachmentMimeTypes.add("text/plain");
		posting.attachmentMimeTypes.add("application/pdf");
		posting.attachmentMimeTypes.add("application/x-shockwave-flash");
		posting.attachmentMimeTypes.add("application/vnd.adobe.flash.movie");
		posting.attachmentMimeTypes.add("application/epub+zip");
		posting.attachmentMimeTypes.add("application/zip");
		posting.attachmentMimeTypes.add("application/x-7z-compressed");
		posting.attachmentMimeTypes.add("application/x-rar-compressed");
		posting.attachmentMimeTypes.add("application/x-tar");
		posting.attachmentMimeTypes.add("application/x-gzip");
		posting.attachmentMimeTypes.add("application/x-bzip2");
		posting.attachmentMimeTypes.add("application/download");
		posting.hasCountryFlags = true;
		return posting;
	}

	@Override
	public Deleting obtainDeletingConfiguration(String boardName) {
		Deleting deleting = new Deleting();
		deleting.password = true;
		deleting.multiplePosts = true;
		deleting.optionFilesOnly = true;
		return deleting;
	}

	public boolean isTagSupported(String boardName, int tag) {
		if (tag == DollchanChanMarkup.TAG_CODE) {
			return get(boardName, KEY_CODE_ENABLED, false);
		}
		return false;
	}
}
