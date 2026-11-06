import org.springframework.security.crypto.bcrypt.BCrypt;

public class HashPassword {
    public static void main(String[] args) {
        String password = System.getenv("OCH_BOOTSTRAP_PASSWORD");
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("Set OCH_BOOTSTRAP_PASSWORD to at least 12 characters");
        }
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt()));
    }
}
