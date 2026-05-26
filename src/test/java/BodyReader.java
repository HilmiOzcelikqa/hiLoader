import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class BodyReader {
    public static String readBase64FromTxt(String filePath) { // txtden içeriği okur
        try {
            File file = new File(filePath);

            return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
