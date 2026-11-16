// Computes the directory name Gradle's wrapper uses under
// $GRADLE_USER_HOME/wrapper/dists/<dist-name>/<hash>/ for a distribution URL:
// base36(MD5(url)). Run with: java GradleWrapperHash.java <distributionUrl>
import java.math.BigInteger;
import java.security.MessageDigest;

public class GradleWrapperHash {
    public static void main(String[] args) throws Exception {
        byte[] md5 = MessageDigest.getInstance("MD5").digest(args[0].getBytes("UTF-8"));
        System.out.println(new BigInteger(1, md5).toString(36));
    }
}
