package app;

public final class PostcodeUtil {

    private PostcodeUtil() {
    }

    public static String trim(String postCode) {
        return postCode == null ? "" : postCode.trim();
    }

    public static boolean matchesTrimmed(String left, String right) {
        return trim(left).equals(trim(right));
    }
}
