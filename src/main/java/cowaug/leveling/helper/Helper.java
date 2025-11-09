package cowaug.leveling.helper;

public class Helper {
    @SuppressWarnings("unchecked")
    public static <T> T CastFrom(Object object) {
        if (object == null) {
            return null;
        }
        return (T) object;
    }
}
