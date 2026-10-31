import com.example.pocketpythonide.PackInputLimits;
import java.io.IOException;

public final class PackInputLimitsSmokeTest {
    private static void rejects(String path, long size, long previous) throws Exception {
        try {
            PackInputLimits.checkedTotal(path, size, previous);
            throw new AssertionError("Expected rejection for " + path);
        } catch (IOException expected) {
            if (!expected.getMessage().contains(path))
                throw new AssertionError("Missing offending path: " + expected.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        long mib = 1024L * 1024;
        if (PackInputLimits.checkedTotal("main.c", mib, 7 * mib) != 8 * mib)
            throw new AssertionError("Allowed boundary rejected");
        rejects("large.dat", mib + 1, 0);
        rejects("more.c", 1, 8 * mib);
        System.out.println("Pack input limits: boundary and named failures OK");
    }
}
