package chan.content.model;

import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import chan.text.ParseException;

public interface PostsParser {
    ArrayList<Posts> convertThreads(InputStream input) throws IOException, ParseException;
    Posts convertPosts(InputStream input, Uri threadUri) throws IOException, ParseException;
    ArrayList<Post> convertSearch(InputStream input) throws IOException, ParseException;
}
