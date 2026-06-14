import org.springframework.security.crypto.bcrypt.*;

public class GenHash {
    public static void main(String[] args) {
        var encoder = new BCryptPasswordEncoder();
        String raw = args.length > 0 ? args[0] : "123456";
        System.out.println(encoder.encode(raw));
    }
}
