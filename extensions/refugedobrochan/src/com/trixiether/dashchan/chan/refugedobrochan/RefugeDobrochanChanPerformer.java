package com.trixiether.dashchan.chan.refugedobrochan;

import android.net.Uri;
import java.net.HttpURLConnection;
import chan.content.InvalidResponseException;
import chan.content.VichanChanPerformer;
import chan.http.HttpException;
import chan.http.HttpRequest;
import chan.http.HttpResponse;
import chan.http.MultipartEntity;
import chan.text.ParseException;

public class RefugeDobrochanChanPerformer extends VichanChanPerformer {
    @Override
    public ReadContentResult onReadContent(ReadContentData data) throws HttpException, InvalidResponseException {
        if (data.uri.toString().contains("/thumb/") && data.uri.toString().endsWith(".png")) {
            HttpResponse response = new HttpRequest(data.uri, data).setSuccessOnly(false).perform();
            if (response.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
            {
                Uri webpThumbUri = Uri.parse(data.uri.toString().replace(".png", ".webp"));
                return new ReadContentResult(new HttpRequest(webpThumbUri, data.direct).perform());
            }
        }
        return super.onReadContent(data);
    }

    @Override
    protected void parseAntispamFields(String text, MultipartEntity entity) throws ParseException {
        RefugeDobrochanAntispamFieldsParser.parseAndApply(text, entity, "board", "thread", "name", "email",
                "subject", "body", "password", "file", "spoiler", "json_response", "reason", "report");
    }
}
