import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Test-only verification using the same encoder as RuoYi SecurityUtils. */
class VerifyBootstrapPassword
{
    public static void main(String[] args) throws Exception
    {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String password = input.readLine();
        String hash = input.readLine();
        if (password == null || hash == null || !new BCryptPasswordEncoder().matches(password, hash))
        {
            System.exit(1);
        }
        System.out.println("PASSWORD_MATCH");
    }
}
