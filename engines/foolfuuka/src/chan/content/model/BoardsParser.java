package chan.content.model;

import java.io.IOException;
import java.io.InputStream;

import chan.text.ParseException;

public interface BoardsParser {
    BoardCategory convert(InputStream input) throws IOException, ParseException;
}
